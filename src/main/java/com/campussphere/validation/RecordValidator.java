package com.campussphere.validation;

import com.campussphere.model.*;
import com.campussphere.exception.CampusException;
import java.time.*;
import java.net.URI;
import java.util.*;

public final class RecordValidator {
    private RecordValidator() { }
    public static Map<String,Object> validate(ModuleSpec spec,Map<String,Object> input) {
        Set<String> known=new HashSet<>(); spec.fields().forEach(f -> known.add(f.key()));
        if(!known.containsAll(input.keySet())) throw new CampusException("This form contains a field that cannot be edited.");
        Map<String,Object> result=new LinkedHashMap<>();
        for(FieldSpec f:spec.fields()) {
            Object value=input.get(f.key());
            if(value instanceof String s) value=s.trim().isEmpty()?null:s.trim();
            if(f.required() && value==null) throw new CampusException("Enter " + f.label().toLowerCase() + ".");
            if(value!=null) {
                try {
                    value=switch(f.type()) {
                        case "integer","reference" -> Long.parseLong(value.toString());
                        case "decimal" -> new java.math.BigDecimal(value.toString());
                        case "date" -> LocalDate.parse(value.toString()).toString();
                        case "datetime" -> LocalDateTime.parse(value.toString()).toString();
                        default -> value.toString();
                    };
                } catch(Exception e) { throw new CampusException("Enter a valid " + f.label().toLowerCase() + "."); }
                if(f.type().equals("choice") && !f.choices().contains(value.toString())) throw new CampusException("Choose a valid " + f.label().toLowerCase() + ".");
                if(f.type().equals("email") && !value.toString().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new CampusException("Enter a valid university email address.");
                int max=f.type().equals("textarea") ? 10000 : f.key().equals("url") ? 2048 : f.type().equals("email") ? 191 : 255;
                if(value.toString().length()>max) throw new CampusException(f.label()+" is too long (maximum "+max+" characters).");
                if(f.key().equals("url")) { try {
                    URI uri=URI.create(value.toString());
                    boolean web=Set.of("http","https").contains(Objects.toString(uri.getScheme(),"")) && uri.getHost()!=null;
                    java.nio.file.Path localPath="file".equals(uri.getScheme())?java.nio.file.Path.of(uri).toAbsolutePath().normalize():null;
                    boolean local=localPath!=null && (localPath.startsWith(java.nio.file.Path.of("data","materials").toAbsolutePath().normalize()) || localPath.startsWith(java.nio.file.Path.of("data","library").toAbsolutePath().normalize())) && java.nio.file.Files.isRegularFile(localPath);
                    if(!web&&!local) throw new Exception();
                } catch(Exception e) { throw new CampusException("Use a full https:// address or a document uploaded to Namma Sphere."); } }
            }
            result.put(f.key(),value);
        }
        return result;
    }
}
