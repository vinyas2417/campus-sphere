package com.campussphere.service;

import com.campussphere.repository.*;
import com.campussphere.model.*;
import com.campussphere.exception.CampusException;
import org.mindrot.jbcrypt.BCrypt;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {
    private static final String DUMMY_HASH = BCrypt.hashpw("nonexistent-account-password", BCrypt.gensalt(10));
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;
    private final JdbcRepository repository;
    private final WorkflowRepository work;
    private final Session session;
    private final Map<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();
    public AuthService(JdbcRepository repository,Session session) { this.repository=repository; this.session=session; work=new WorkflowRepository(repository.database()); }
    public boolean needsSetup() { return repository.findAll("users").isEmpty(); }
    public User setupAdmin(String name,String username,String email,String password) {
        validate(name,username,email,password); String hash=BCrypt.hashpw(password,BCrypt.gensalt(12));
        User user=work.transaction(tx -> {
            if (!tx.all("users").isEmpty()) throw new CampusException("This campus already has an administrator. Sign in to continue.");
            Row row=tx.insert("users",account(name,username,email,hash,Role.ADMIN));
            tx.insert("administrators",Map.of("user_id",row.id(),"name",name.trim())); return user(row);
        }); session.signIn(user); return user;
    }
    public User login(String username,String password) {
        return authenticate(username,password,null);
    }
    public User login(String username,String password,Role expectedRole) {
        if(expectedRole==null) throw new CampusException("Choose a sign-in portal to continue.");
        return authenticate(username,password,expectedRole);
    }
    private User authenticate(String username,String password,Role expectedRole) {
        if(username==null || username.isBlank()) throw new CampusException("Please enter your username.");
        if(password==null || password.isEmpty()) throw new CampusException("Please enter your password.");
        String identifier=username.trim();
        String key=identifier.toLowerCase(Locale.ROOT);
        LoginAttempts attempts=loginAttempts.computeIfAbsent(key,ignored->new LoginAttempts());
        synchronized(attempts) {
            long now=System.currentTimeMillis();
            if(attempts.lockedUntil>now) throw new CampusException("Too many sign-in attempts. Wait a few minutes and try again.");
            if(attempts.lockedUntil!=0) { attempts.failures=0; attempts.lockedUntil=0; }
        }
        List<Row> matches;
        try { matches=repository.query("SELECT * FROM users WHERE LOWER(username)=LOWER(?) OR LOWER(email)=LOWER(?) LIMIT 1",identifier,identifier); }
        catch(RuntimeException databaseFailure) { throw new CampusException("Authentication service is temporarily unavailable. Please try again later.",databaseFailure); }
        Row row=matches.isEmpty()?null:matches.get(0);
        boolean validPassword;
        try { validPassword=BCrypt.checkpw(password,row==null?DUMMY_HASH:row.text("password_hash")); }
        catch(IllegalArgumentException malformedHash) { validPassword=false; }
        if(row==null || !validPassword) { failedLogin(key,attempts); throw new CampusException("Invalid username or password. Please try again."); }
        Role role;
        try { role=Role.valueOf(row.text("role")); }
        catch(IllegalArgumentException | NullPointerException invalidRole) { failedLogin(key,attempts); throw new CampusException("Your account role could not be verified. Please contact the administration."); }
        if(!row.text("account_status").equals("ACTIVE")) { failedLogin(key,attempts); throw new CampusException("Your account is currently inactive. Please contact the administration."); }
        if(expectedRole!=null && role!=expectedRole) throw new CampusException("Invalid username or password. Please try again.");
        synchronized(attempts) { attempts.failures=0; attempts.lockedUntil=0; }
        User user=new User(row.id(),row.text("username"),row.text("name"),role);
        session.signIn(user); return user;
    }
    private void failedLogin(String key,LoginAttempts attempts) {
        synchronized(attempts) {
            attempts.failures++;
            if(attempts.failures>=MAX_ATTEMPTS) { attempts.lockedUntil=System.currentTimeMillis()+LOCK_MILLIS; attempts.failures=0; }
        }
    }
    public void logout() { session.clear(); }
    public void createAccount(String name,String username,String email,String password,Role role,long profileId) {
        admin(); validate(name,username,email,password); String hash=BCrypt.hashpw(password,BCrypt.gensalt(12));
        work.transaction(tx -> {
            admin(); unique(tx,username,email);
            Row profile=null; String table=role==Role.STUDENT ? "students":"faculty";
            if(role!=Role.ADMIN) { profile=tx.require(table,profileId); if(profile.number("user_id")!=0) throw new CampusException("This profile already has an account."); }
            Row row=tx.insert("users",account(name,username,email,hash,role));
            if(role==Role.ADMIN) tx.insert("administrators",Map.of("user_id",row.id(),"name",name.trim()));
            else tx.update(table,profile.id(),Map.of("user_id",row.id()));
            return null;
        });
    }
    public void changePassword(String oldPassword,String newPassword) {
        User actor=session.requireUser(); validatePassword(newPassword);
        String hash=BCrypt.hashpw(newPassword,BCrypt.gensalt(12));
        work.transaction(tx -> { Row row=tx.require("users",actor.id()); if(!BCrypt.checkpw(oldPassword,row.text("password_hash"))) throw new CampusException("Your current password is incorrect."); tx.update("users",actor.id(),Map.of("password_hash",hash)); return null; });
    }
    public void resetPassword(long userId,String newPassword) {
        admin(); validatePassword(newPassword); String hash=BCrypt.hashpw(newPassword,BCrypt.gensalt(12));
        work.transaction(tx -> { admin(); tx.require("users",userId); tx.update("users",userId,Map.of("password_hash",hash)); return null; });
    }
    private void admin() { if(session.requireUser().role()!=Role.ADMIN) throw new CampusException("Only an administrator can manage accounts."); }
    private static User user(Row row) { return new User(row.id(),row.text("username"),row.text("name"),Role.valueOf(row.text("role"))); }
    private static Map<String,Object> account(String name,String username,String email,String hash,Role role) { return Map.of("name",name.trim(),"username",username.trim().toLowerCase(Locale.ROOT),"email",email.trim().toLowerCase(Locale.ROOT),"password_hash",hash,"role",role.name(),"account_status","ACTIVE","created_at",LocalDateTime.now().toString()); }
    private static void unique(WorkflowRepository.Transaction tx,String username,String email) { if(tx.all("users").stream().anyMatch(r -> r.text("username").equalsIgnoreCase(username.trim()) || r.text("email").equalsIgnoreCase(email.trim()))) throw new CampusException("That username or email already has an account."); }
    private static void validate(String name,String username,String email,String password) {
        if(name==null || name.isBlank() || name.length()>255) throw new CampusException("Enter a full name, up to 255 characters.");
        if(username==null || !username.matches("[A-Za-z0-9._-]{3,64}")) throw new CampusException("Use a username of 3–64 letters, numbers, dots, hyphens or underscores.");
        if(email==null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>191) throw new CampusException("Enter a valid university email address.");
        validatePassword(password);
    }
    private static void validatePassword(String password) { if(password==null || password.length()<6 || password.getBytes(StandardCharsets.UTF_8).length>72) throw new CampusException("Use a password with at least 6 characters and at most 72 UTF-8 bytes."); }
    private static final class LoginAttempts { private int failures; private long lockedUntil; }
}
