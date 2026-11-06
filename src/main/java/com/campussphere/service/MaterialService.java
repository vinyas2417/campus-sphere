package com.campussphere.service;

import com.campussphere.model.*;
import com.campussphere.exception.CampusException;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Copies a user-selected document into managed local storage before saving its catalog record. */
public final class MaterialService {
    private final CampusService campus; private final Session session;
    public MaterialService(CampusService campus,Session session) {this.campus=campus;this.session=session;}
    public Row upload(Path source,Map<String,Object> input) {
        User actor=session.requireUser();
        if(actor.role()==Role.STUDENT) throw new CampusException("Only faculty and administrators can upload course materials.");
        long courseId=Long.parseLong(input.get("course_id").toString());
        if(campus.list("courses").stream().noneMatch(c->c.id()==courseId)) throw new CampusException("Choose one of your assigned courses.");
        Path copied=null;
        try {
            if(!Files.isRegularFile(source) || Files.size(source)>25L*1024*1024) throw new CampusException("Select a file no larger than 25 MB.");
            String name=source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]","_");
            Path directory=Path.of("data","materials").toAbsolutePath().normalize();Files.createDirectories(directory);
            copied=directory.resolve(UUID.randomUUID()+"-"+name); Files.copy(source,copied);
            Map<String,Object> values=new LinkedHashMap<>(input);values.put("url",copied.toUri().toString());
            return campus.save("study_materials",null,values);
        } catch(IOException error) { cleanup(copied);throw new CampusException("The document could not be copied. Check file and storage access.",error); }
        catch(RuntimeException error) {cleanup(copied);throw error;}
    }

    public Row uploadLibrary(Path source,Map<String,Object> input) {
        User actor=session.requireUser();
        if(actor.role()==Role.STUDENT)throw new CampusException("Only faculty and administrators can upload e-library books.");
        Object courseValue=input.get("course_id");
        if(actor.role()==Role.FACULTY&&(courseValue==null||campus.list("courses").stream().noneMatch(c->c.id()==Long.parseLong(courseValue.toString()))))throw new CampusException("Choose one of your assigned courses for this book.");
        Path copied=null;
        try{
            if(!Files.isRegularFile(source)||Files.size(source)==0||Files.size(source)>25L*1024*1024)throw new CampusException("Select a non-empty PDF no larger than 25 MB.");
            byte[] signature=new byte[5];try(var in=Files.newInputStream(source)){if(in.read(signature)!=5||!Arrays.equals(signature,"%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new CampusException("The selected file is not a valid PDF.");}
            Path directory=Path.of("data","library").toAbsolutePath().normalize();Files.createDirectories(directory);
            String name=source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]","_");copied=directory.resolve(UUID.randomUUID()+"-"+name);Files.copy(source,copied);
            Map<String,Object> values=new LinkedHashMap<>(input);values.put("url",copied.toUri().toString());
            return campus.save("library_resources",null,values);
        }catch(IOException error){cleanup(copied);throw new CampusException("The e-library PDF could not be stored.",error);}
        catch(RuntimeException error){cleanup(copied);throw error;}
    }
    private static void cleanup(Path path) {if(path!=null)try{Files.deleteIfExists(path);}catch(IOException ignored){}}
}
