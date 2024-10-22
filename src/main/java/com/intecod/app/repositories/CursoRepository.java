package com.intecod.app.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.intecod.app.entities.Curso;
import java.util.List;
import java.util.Optional;
import java.util.Date;

public interface CursoRepository extends MongoRepository<Curso, String> {

    List<Curso> findByProfesorId(String profesorId);

    Optional<Curso> findByCodigoCurso( String codigoCurso );

    List<Curso> findByProfesorIdAndFechaCreacionBetween( String profesorId, Date fechaInicio, Date fechaFin );
    
}