package com.intecod.app.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.intecod.app.entities.EvaluacionEstudiante;
import com.intecod.app.entities.Leccion;
import com.intecod.app.services.EvaluacionEstudianteService;

import com.intecod.app.entities.User;
import com.intecod.app.services.UserService;

import com.intecod.app.entities.Notificacion;
import com.intecod.app.services.NotificacionService;

import com.intecod.app.entities.Curso;
import com.intecod.app.services.CursoService;

import com.intecod.app.entities.Leccion;
import com.intecod.app.services.LeccionService;

import com.intecod.app.dto.EmailRequest;
import com.intecod.app.services.EmailService;



import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/evaluaciones_estudiante")
public class EvaluacionEstudianteController {

    @Autowired
    private EvaluacionEstudianteService service;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private CursoService cursoService;

    @Autowired
    private LeccionService leccionService;

    @Autowired
    private EmailService emailService;


    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody Map<String, Object> request ) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );

        Map<String, Object> curso = (Map<String, Object>) request.get( "curso" );
        Map<String, Object> evaluacion = (Map<String, Object>) request.get( "evaluacion" );
        List<Map<String, Object>> cuestionarioEstudiante = (List<Map<String, Object>>) evaluacion.get("cuestionarioEstudiante");

        // evaluacionEstudiante.setUsuarioId( users.getId() );

          // Extraer IDs
        String usuarioId = users.getId();
        String profesorId = (String) curso.get("profesorId");
        String cursoId = (String) curso.get("id");
        String evaluacionId = (String) evaluacion.get("evaluacionId");

        Boolean aprobado = false;
        String aprobadoMensaje = "";

        // Buscar si ya existe una evaluación de este usuario para este curso, evaluación y profesor
        Optional<EvaluacionEstudiante> evaluacionExistenteOpt = service.findByUsuarioIdAndCursoIdAndEvaluacionIdAndProfesorId( usuarioId, cursoId, evaluacionId, profesorId );

        EvaluacionEstudiante evaluacionEstudiante;

        if (evaluacionExistenteOpt.isPresent()) {
            // Para la evaluación existente
            evaluacionEstudiante = evaluacionExistenteOpt.get();
            Number ponderacion = (Number) evaluacion.get("ponderacion");
            evaluacionEstudiante.setPonderacion(ponderacion.intValue());
            evaluacionEstudiante.setEnlaceResultado((String) evaluacion.get("enlaceResultado"));
            evaluacionEstudiante.setCuestionarioEstudiante((List<Object>) evaluacion.get("cuestionarioEstudiante"));
            evaluacionEstudiante.setFechaEvaluacion(Date.from(Instant.now()));
        } else {
            // Para la nueva evaluación
            evaluacionEstudiante = new EvaluacionEstudiante();
            evaluacionEstudiante.setUsuarioId(usuarioId);
            evaluacionEstudiante.setProfesorId(profesorId);
            evaluacionEstudiante.setCursoId(cursoId);
            evaluacionEstudiante.setEvaluacionId(evaluacionId);
            evaluacionEstudiante.setEnlaceResultado((String) evaluacion.get("enlaceResultado"));
            evaluacionEstudiante.setCuestionarioEstudiante((List<Object>) evaluacion.get("cuestionarioEstudiante"));
            Number ponderacion = (Number) evaluacion.get("ponderacion");
            evaluacionEstudiante.setPonderacion(ponderacion.intValue());
            evaluacionEstudiante.setFechaEvaluacion(Date.from(Instant.now()));
        }

        boolean tienePreguntasLink = cuestionarioEstudiante.stream()
        .anyMatch(p -> "link".equals(((Map) p.get("pregunta")).get("tipo")));

        System.out.println("Cuestionario: " + cuestionarioEstudiante);
        System.out.println("Preguntas link: " + tienePreguntasLink);

        int puntajePorRespuesta = 100 / cuestionarioEstudiante.size();

        int puntajeTotal = cuestionarioEstudiante.stream()
        .filter(p -> !"link".equals(((Map) p.get("pregunta")).get("tipo")))
        .mapToInt(p -> {
            Map opcion = (Map) p.get("opcion");
            boolean correcta = (boolean) opcion.get("correcta");
            return correcta ? puntajePorRespuesta : 0;
        })
        .sum();

        System.out.println("Puntaje total: " + puntajeTotal);
        evaluacionEstudiante.setPonderacion(puntajeTotal);

            EvaluacionEstudiante savedEvaluacionEstudiante = service.save( evaluacionEstudiante );

            aprobado = puntajeTotal > 60;
            if (!tienePreguntasLink) {
                aprobadoMensaje = aprobado ? "Evaluación aprobada" : "Evaluación reprobada";
            }else{
                aprobadoMensaje = "Evaluación pendiente de revisión";
            }

            if( aprobado == true ){

                try {
                 
                    EmailRequest emailRequest = new EmailRequest();
                    emailRequest.setTo( users.getCorreo() );
                    emailRequest.setSubject("Felicitaciones");
                    String emailText = "En hora buenas has finalizado una evaluación sobre el curso " + curso.get("nombre") + " con éxito. ";
                    emailRequest.setText( emailText );
                    emailService.sendEmail(emailRequest);

                } catch (Exception e) {
                    // TODO: handle exception
                }

            }



        // armar notificaicones 

            Notificacion notificacion = new Notificacion();
            notificacion.setUsuarioEmisorId( users.getId());
            notificacion.setUsuarioReceptorId( (String) curso.get( "profesorId") );
            notificacion.setContenido( "Debes calificar una nueva evalucion" );
            notificacion.setTipoNotificacion( "Calificar");
            notificacion.setFechaEnvio( Date.from( Instant.now()) );
            notificacion.setEvaluacionXEstudianteId( savedEvaluacionEstudiante.getId() );
            notificacion.setLeido( false );

            System.out.println( "-------" );
            System.out.println( notificacion );

            Notificacion savedNotificacion = notificacionService.save( notificacion );
            
        /// receptor empujar notificaion

            User profesor = userService.findById( ( String ) curso.get( "profesorId" ) );
            profesor.getNotificaciones(  ).add( savedNotificacion.getId() );
            userService.save( profesor );
            System.out.println( "-------" );
            System.out.println( profesor );


        /// enviar correo al terminar el curso
        //////////////////////////////////////////////

            Optional<Curso> cursoEvaluacionID = cursoService.findById( cursoId );

            if( cursoEvaluacionID.isPresent() ){

                Curso cursoEvaluacionEstudiante = cursoEvaluacionID.get();

                List<String> leccionesId = cursoEvaluacionEstudiante.getLecciones() ;
                boolean banderaAprobado = true;

                for (String leccionId : leccionesId) {

                    Optional<Leccion> leccion = leccionService.findById(leccionId);

                    if (leccion.isPresent()) {

                        Leccion leccionObj = leccion.get();
                        List<String> evaluaciones = leccionObj.getEvaluaciones();

                        for (String evaluacionCurso : evaluaciones) {
                            Optional<EvaluacionEstudiante> evaluacionEstudianteCurso = service.findByCursoIdAndEvaluacionId( cursoId, evaluacionCurso );

                            if( evaluacionEstudianteCurso.isPresent() ){

                                EvaluacionEstudiante eva_x_estudiante = evaluacionEstudianteCurso.get();

                                if (eva_x_estudiante.getPonderacion() <= 59) {
                                    banderaAprobado = false;
                                    break;
                                }
                            }else{
                                banderaAprobado = false;
                                break;
                            }
                        }

                        if (!aprobado) {
                            break;
                        }

                    }

                }

                if( banderaAprobado == true ){

                    EmailRequest emailRequestFinalizarCurso = new EmailRequest();
                    emailRequestFinalizarCurso.setTo( users.getCorreo() );
                    emailRequestFinalizarCurso.setSubject("Felicitaciones");
                    String emailText = "En hora buenas has finalizado el curso " + curso.get("nombre") + " con éxito. ";
                    emailRequestFinalizarCurso.setText( emailText );
                    emailService.sendEmail( emailRequestFinalizarCurso );

                    
                    List<String> completados = cursoEvaluacionEstudiante.getCompletados();

                    if (completados == null) {
                        completados = new ArrayList<>();
                        cursoEvaluacionEstudiante.setCompletados(completados); 
                    }
                    
                    if( !completados.contains( users.getId( ) ) ){

                        completados.add(users.getId());
                        cursoService.save(cursoEvaluacionEstudiante);

                    } else {
                        System.out.println("El usuario ya está en la lista de completados.");
                    }

                }

            }else{}

        //////////////////////////////////////////////

        Map<String, Object> response = new HashMap<>();

        response.put("valid", true);
        response.put("message", "Evaluación del estudiante creada con éxito");
        response.put("aprobado", aprobado);
        response.put("aprobadoMensaje", aprobadoMensaje);
        // response.put("data", savedEvaluacionEstudiante);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        Optional<EvaluacionEstudiante> evaluacionEstudiante = service.findById(id);
        Map<String, Object> response = new HashMap<>();
        if (evaluacionEstudiante.isPresent()) {
            response.put("valid", true);
            response.put("message", "Evaluación del estudiante encontrada");
            response.put("data", evaluacionEstudiante.get());
            return ResponseEntity.ok(response);
        } else {
            response.put("valid", false);
            response.put("message", "Evaluación del estudiante no encontrada");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("message", "Lista de evaluaciones de estudiantes");
        response.put("data", service.findAll());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('profesor')")
    @PutMapping()
    public ResponseEntity<?> update( @RequestBody Map<String, String> request ) {
        
        Map<String, Object> response = new HashMap<>();
        
        String evaluacion =  request.get( "evaluacionId" );
        String ponderacionStr = request.get( "ponderacion" );
        
        Optional<EvaluacionEstudiante> evaluacionEstudianteOpt = service.findById( evaluacion );

        System.out.println(evaluacionEstudianteOpt);

        if ( evaluacionEstudianteOpt.isPresent() ){
        
            EvaluacionEstudiante evaluacionEstudiante = evaluacionEstudianteOpt.get();
    
            int ponderacion = Integer.parseInt(ponderacionStr);
            evaluacionEstudiante.setPonderacion(ponderacion);
    
            service.save(evaluacionEstudiante);


            // armar notificaicones 
            Notificacion notificacion = new Notificacion();
            notificacion.setUsuarioEmisorId( evaluacionEstudiante.getProfesorId() );
            notificacion.setUsuarioReceptorId( evaluacionEstudiante.getUsuarioId() );
            notificacion.setContenido( "Se actualizo la nota del parcial" );
            notificacion.setTipoNotificacion( "Mensaje");
            notificacion.setFechaEnvio( Date.from( Instant.now()) );
            notificacion.setEvaluacionXEstudianteId( evaluacionEstudiante.getId() );
            notificacion.setLeido( false );

            Notificacion savedNotificacion = notificacionService.save( notificacion );
            
            /// receptor empujar notificaion
            User user = userService.findById( evaluacionEstudiante.getUsuarioId()  );
            user.getNotificaciones(  ).add( savedNotificacion.getId() );
            userService.save( user );
    
            response.put("valid", true);
            response.put("message", "Evaluación del estudiante actualizada con éxito");
            return ResponseEntity.ok(response);

        } else {
          
            response.put("valid", false);
            response.put("message", "Evaluación no encontrada");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        Optional<EvaluacionEstudiante> evaluacionEstudiante = service.findById(id);
        Map<String, Object> response = new HashMap<>();
        if (evaluacionEstudiante.isPresent()) {
            service.delete(id);
            response.put("valid", true);
            response.put("message", "Evaluación del estudiante eliminada con éxito");
            return ResponseEntity.noContent().build();
        } else {
            response.put("valid", false);
            response.put("message", "Evaluación del estudiante no encontrada");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    private ResponseEntity<?> validation(BindingResult result) {
        Map<String, Object> errors = new HashMap<>();
        result.getFieldErrors().forEach(err -> {
            errors.put(err.getField(), "El campo " + err.getField() + " " + err.getDefaultMessage());
        });
        Map<String, Object> response = new HashMap<>();
        response.put("valid", false);
        response.put("message", "Errores de validación");
        response.put("errors", errors);
        return ResponseEntity.badRequest().body(response);
    }
}