package com.intecod.app.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.intecod.app.entities.Curso;
import com.intecod.app.services.CursoService;

import com.intecod.app.entities.User;
import com.intecod.app.services.UserService;

// import io.jsonwebtoken.lang.Arrays;

import com.intecod.app.entities.Leccion;
import com.intecod.app.services.LeccionService;

import com.intecod.app.entities.Evaluacion;
import com.intecod.app.services.EvaluacionService;


import com.intecod.app.entities.EvaluacionEstudiante;
import com.intecod.app.services.EvaluacionEstudianteService;



import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cursos")
public class CursoController {

    @Autowired
    private CursoService service;

    @Autowired
    private UserService userService;

    @Autowired
    private LeccionService leccionService;

    @Autowired
    private EvaluacionService evaluacionService;

    @Autowired
    private EvaluacionEstudianteService evaluacionEstudianteService;


    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody Curso curso, BindingResult result) {
        
        if (result.hasFieldErrors()) {
            return validation(result);
        }

        if (curso.getTipoCurso() == null || (!curso.getTipoCurso().equalsIgnoreCase("ortografia") && !curso.getTipoCurso().equalsIgnoreCase("caligrafia"))) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", "Tipo de curso inválido. Debe ser 'ortografia' o 'caligrafia'.");
            return ResponseEntity.badRequest().body(response);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );

        curso.setProfesorId( users.getId() );


        List<String> listaTipoLecciones = curso.getTipoCurso().equals("ortografia") ?
        Arrays.asList( "ortografia", "ortografía", "gramática") :
        Arrays.asList( "caligrafía" );

        List<String> lecciones_asignar = obtenerLeccionesPorTipo( listaTipoLecciones ) ;
        System.out.println( lecciones_asignar );
        curso.setLecciones( lecciones_asignar );

        curso.setEstudiantes( List.of() );

        if (curso.getCodigoCurso() == null || curso.getCodigoCurso().isEmpty()) {
            curso.setCodigoCurso(generarCodigoUnico());
        }

        Curso savedCurso = service.save(curso);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("message", "Curso creado con éxito");
        response.put("data", savedCurso);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    private  List<String> obtenerLeccionesPorTipo( List<String> tipoCurso ){

        System.out.println( "--------------" );
        System.out.println( tipoCurso );
        List<Leccion> lecciones = leccionService.findByTipoLeccionIn( tipoCurso );
        System.out.println( lecciones );
        return lecciones.stream().map( Leccion::getId ).collect( Collectors.toList() );
        
    }

    private String generarCodigoUnico() {
        return "CUR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        Optional<Curso> curso = service.findById(id);
        Map<String, Object> response = new HashMap<>();
        if (curso.isPresent()) {
            response.put("valid", true);
            response.put("message", "Curso encontrado");
            response.put("data", curso.get());
            return ResponseEntity.ok(response);
        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    
    @GetMapping
    public ResponseEntity<?> getAll() {

        Map<String, Object> response = new HashMap<>();


        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );

    
        List<Curso> cursos = service.findByProfesorId( users.getId() );

        response.put("valid", true);
        response.put("message", "Lista de cursos");
        response.put("data", cursos );

        return ResponseEntity.ok(response);

    }

    
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Curso curso, BindingResult result) {
        if (result.hasErrors()) {
            return validation(result);
        }

        Optional<Curso> existingCurso = service.findById(id);
        Map<String, Object> response = new HashMap<>();

        if (existingCurso.isPresent()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String correo = auth.getPrincipal().toString();
            User profesor = userService.findByCorreo(correo);
            curso.setProfesorId(profesor.getId());

            curso.setId(id);
            Curso updatedCurso = service.save(curso);
            response.put("valid", true);
            response.put("message", "Curso actualizado con éxito");
            response.put("data", updatedCurso);
            return ResponseEntity.ok(response);
        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {

        Optional<Curso> curso = service.findById(id);

        Map<String, Object> response = new HashMap<>();

        if (curso.isPresent()) {

            // 1 elimiinar de los estudiantes asignados
            Iterable<User> usuariosConCurso = userService.findAll();

            // Paso 2: Eliminar el curso de la lista de cursos asignados y guardar
            for (User user : usuariosConCurso) {
                user.getCursos_asignados().remove(id);
                userService.save(user);  // Guardar el usuario actualizado
            }
            // 2 eliminar las evaluaciones x estudiantes de ese curso -- tema de espacio
            evaluacionEstudianteService.eliminarPorCursoId(id);

            // 3 eliminar objeto
            service.delete(id);

            response.put("valid", true);
            response.put("message", "Curso eliminado con éxito");
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } else {
            
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
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


    @PostMapping("/asignar")
    public ResponseEntity<?> asignarEstudiante(@RequestBody Map<String, String> request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo(correo);

        Map<String, Object> response = new HashMap<>();

        String codigoCurso = request.get("codigoCurso");

        Optional<Curso> curso = service.findByCursoCodigo(codigoCurso);

        if (curso.isPresent()) {

            Curso cursoAsignar = curso.get();

            // Verificar si el estudiante está en la lista de bloqueados del curso
            if (cursoAsignar.getBloqueados() != null && cursoAsignar.getBloqueados().contains(users.getId())) {
                response.put("valid", false);
                response.put("message", "No se puede asignar al curso por favor consulte con su catedrático.");
                return ResponseEntity.ok(response);
            }

            // Inicializar listas si son null para evitar NullPointerException
            if (users.getCursos_asignados() == null) {
                users.setCursos_asignados(new ArrayList<>());
            }
            if (cursoAsignar.getEstudiantes() == null) {
                cursoAsignar.setEstudiantes(new ArrayList<>());
            }

            // Asignar el curso al estudiante y viceversa
            users.getCursos_asignados().add(cursoAsignar.getId());
            userService.save(users);

            cursoAsignar.getEstudiantes().add(users.getId());
            service.save(cursoAsignar);

            response.put("valid", true);
            response.put("message", "Curso asignado con éxito");
            return ResponseEntity.ok(response);

        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/bloquear")
    public ResponseEntity<?> bloquearEstudiante(@RequestBody Map<String, String> request) {
    
        Map<String, Object> response = new HashMap<>();
    
        String codigoCurso = request.get("codigoCurso");
        String correoEstudiante = request.get("correoEstudiante");
    
        Optional<Curso> cursoOptional = service.findByCursoCodigo(codigoCurso);
    
        if (cursoOptional.isPresent()) {
            Curso curso = cursoOptional.get();
    
            if (curso.getBloqueados() == null) {
                curso.setBloqueados(new ArrayList<>());
            }
    
            User estudiante = userService.findByCorreo(correoEstudiante);
    
            if (estudiante == null) {
                response.put("valid", false);
                response.put("message", "Estudiante no encontrado con el correo proporcionado.");
                return ResponseEntity.ok(response);
            }
    
            String estudianteId = estudiante.getId();
            String cursoId = curso.getId();
    
            if (curso.getEstudiantes().contains(estudianteId)) {
                curso.getEstudiantes().remove(estudianteId);
            }
    
            if (!curso.getBloqueados().contains(estudianteId)) {
                curso.getBloqueados().add(estudianteId);
            }
    
            if (estudiante.getCursos_asignados() != null) {
                if (estudiante.getCursos_asignados().contains(cursoId)) {
                    estudiante.getCursos_asignados().remove(cursoId);
                    userService.save(estudiante);
                }
            }
    
            service.save(curso);
    
            response.put("valid", true);
            response.put("message", "Estudiante bloqueado exitosamente.");
            return ResponseEntity.ok(response);
    
        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado.");
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/desbloquear")
    public ResponseEntity<?> desbloquearEstudiante(@RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();

        String codigoCurso = request.get("codigoCurso");
        String correoEstudiante = request.get("correoEstudiante");
        Optional<Curso> cursoOptional = service.findByCursoCodigo(codigoCurso);

        if (cursoOptional.isPresent()) {
            Curso curso = cursoOptional.get();

            User estudiante = userService.findByCorreo(correoEstudiante);

            if (estudiante == null) {
                response.put("valid", false);
                response.put("message", "Estudiante no encontrado con el correo proporcionado.");
                return ResponseEntity.ok(response);
            }

            String estudianteId = estudiante.getId();

            if (curso.getBloqueados().contains(estudianteId)) {
                curso.getBloqueados().remove(estudianteId);
                service.save(curso); 

                response.put("valid", true);
                response.put("message", "Estudiante desbloqueado exitosamente.");
            } else {
                response.put("valid", false);
                response.put("message", "El estudiante no está bloqueado.");
            }

            return ResponseEntity.ok(response);

        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado.");
            return ResponseEntity.ok(response);
        }
    }

   
    @GetMapping("/listado_estudiantes_cursos")
    public ResponseEntity<?> estudiantes_cursos(){

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );

        List<Curso> cursos = service.findByProfesorId( users.getId() );


         List<Map<String, Object>> ListaCursosXEstudiante = new ArrayList<>();
        
         for (Curso curso : cursos) {
            Map<String, Object> Curso_X_Estudiante = new HashMap<>();
            for (String estudiante : curso.getEstudiantes()) {
                User user_inscrito = userService.findById(estudiante);
                if (user_inscrito != null) {
                    Curso_X_Estudiante.put("curso", curso);
                    Curso_X_Estudiante.put("estudiante", user_inscrito);
                    ListaCursosXEstudiante.add(Curso_X_Estudiante);
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("message", "Lista de estudiantes del curso");
        response.put("data", ListaCursosXEstudiante);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/listado_estudiantes_x_curso")
    public ResponseEntity<?> listarEstudiantesPorCurso(@RequestParam String cursoId) {

        System.out.println(" ---- estas en listado estudiantes x curso ");
        System.out.println(" ----" + cursoId );
        Optional<Curso> cursoOpt = service.findById(cursoId);
        System.out.println(" ----  " + cursoOpt);

        Map<String, Object> response = new HashMap<>();
        
        if (cursoOpt.isPresent()) {
            Curso curso = cursoOpt.get();
            response.put("valid", true);
            response.put("message", "Lista de estudiantes del curso");
            response.put("data", curso.getEstudiantes());
            return ResponseEntity.ok(response);
        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/listado_bloqueados_x_curso")
    public ResponseEntity<?> listarBloqueadosPorCurso(@RequestParam String cursoId) {
    
        System.out.println(" ---- estás en listado de estudiantes bloqueados por curso ");
        System.out.println(" ---- Curso ID: " + cursoId);
        
        Optional<Curso> cursoOpt = service.findById(cursoId);
        
        Map<String, Object> response = new HashMap<>();
        
        if (cursoOpt.isPresent()) {
            Curso curso = cursoOpt.get();
            
            if (curso.getBloqueados() == null) {
                curso.setBloqueados(new ArrayList<>());
            }
    
            List<Map<String, String>> estudiantesBloqueados = new ArrayList<>();
    
            for (String estudianteId : curso.getBloqueados()) {
                User estudiante = userService.findById(estudianteId);
                if (estudiante != null) {
                    Map<String, String> estudianteInfo = new HashMap<>();
                    estudianteInfo.put("nombre_completo", estudiante.getNombre_completo());
                    estudianteInfo.put("correo", estudiante.getCorreo());
                    estudiantesBloqueados.add(estudianteInfo);
                }
            }
    
            response.put("valid", true);
            response.put("message", "Lista de estudiantes bloqueados del curso");
            response.put("data", estudiantesBloqueados);
            return ResponseEntity.ok(response);
    
        } else {
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    
    @PostMapping("/desasignar")
    public ResponseEntity<?> desasignarEstudiante(@RequestBody Map<String, Object> request) {


        Map<String, Object> estudiante = (Map<String, Object>) request.get("estudiante");
        Map<String, Object> curso = (Map<String, Object>) request.get("curso");
        
        // Acceder a los atributos del estudiante
        String idEstudiante = (String) estudiante.get("id");
        
        // Acceder a los atributos del curso
        String idCurso = (String) curso.get("id");

        // 1 eliminar del curso
            
            Curso cursoOpt = service.findById(idCurso).orElse(new Curso());
            cursoOpt.getEstudiantes().remove(idEstudiante);
            
            // 1.5 agrego a los desasignados si no existe
            if( !cursoOpt.getDesasignados().contains( idEstudiante ) ){
                cursoOpt.getDesasignados().add( idEstudiante );
            }

            service.save( cursoOpt );

        // 2 eliminar de estudiante
            User user = userService.findById( idEstudiante );
            user.getCursos_asignados().remove( idCurso );
            userService.save( user );

        // 3 eliminar de evaluaciones_x_estudiantes -- por ahorrar espacio
        evaluacionEstudianteService.deleteByUsuarioIdAndCursoId( idEstudiante, idCurso );


        Map<String, Object> response = new HashMap<>();
        

        response.put("valid", true);
        response.put("message", "Estudiante desasignado con éxito");
        return ResponseEntity.ok(response);

    }

    
    @GetMapping("/curso_profesor")
    public ResponseEntity<?> listarCursosPorProfesor(@RequestParam String profesorId) {
        List<Curso> cursos = service.findByProfesorId(profesorId);
        Map<String, Object> response = new HashMap<>();
        
        if (!cursos.isEmpty()) {
            response.put("valid", true);
            response.put("message", "Lista de cursos creados por el profesor");
            response.put("data", cursos);
            return ResponseEntity.ok(response);
        } else {
            response.put("valid", false);
            response.put("message", "No se encontraron cursos para el profesor");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/curso_asignados")
    public ResponseEntity<?> getCursosASignados() {

        Map<String, Object> response = new HashMap<>();


        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );
       
        List<Map<String, Object>> listaCursosConEvaluaciones = new ArrayList<>();

        for (String cursosEstudiante : users.getCursos_asignados()) {

            Optional<Curso> cursoOpt = service.findById(cursosEstudiante);
            
            if (cursoOpt.isPresent()) {

                Curso curso = cursoOpt.get();
                Map<String, Object> cursoMap = new HashMap<>();
                
                cursoMap.put("id", curso.getId());
                cursoMap.put("nombre", curso.getNombre());
                cursoMap.put("escuela", curso.getEscuela());
                cursoMap.put("lecciones", curso.getLecciones());
                cursoMap.put("codigoCurso", curso.getCodigoCurso());
                cursoMap.put("profesorId", curso.getProfesorId());
                cursoMap.put("seccion", curso.getSeccion());
                cursoMap.put("tipoCurso", curso.getTipoCurso());
                
                List<EvaluacionEstudiante> evaluaciones = evaluacionEstudianteService.findByUsuarioIdAndCursoIdAndPonderacionGreaterThan(users.getId(), curso.getId(), 60);
                cursoMap.put("totalEvaluacionesAprobadas", evaluaciones.size());

                listaCursosConEvaluaciones.add(cursoMap);
            }
        }

        // Crear la respuesta
        response.put("valid", true);
        response.put("message", "Lista de cursos");
        response.put("data", listaCursosConEvaluaciones); 

        return ResponseEntity.ok(response);

    }
    
    @PostMapping("/contenido_curso")
    public ResponseEntity<?> ObtenerContenidoCurso( @RequestBody Curso request ) {

        String cursoId = request.getId();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );
    
        Optional<Curso> optionalCurso = service.findById(cursoId);
    
        if (!optionalCurso.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", "Curso no encontrado");
            return ResponseEntity.ok(response);
        }
    
        Curso curso = optionalCurso.get();
    
        // Convertir Curso a Map<String, Object>
        Map<String, Object> cursoMap = new HashMap<>();
        cursoMap.put("id", curso.getId());
        cursoMap.put("tipoCurso", curso.getTipoCurso());
        cursoMap.put("nombre", curso.getNombre());
        cursoMap.put("seccion", curso.getSeccion());
        cursoMap.put("escuela", curso.getEscuela());
        cursoMap.put("codigoCurso", curso.getCodigoCurso());
        cursoMap.put("profesorId", curso.getProfesorId());
        cursoMap.put("estudiantes", curso.getEstudiantes());
        cursoMap.put("lecciones", curso.getLecciones());
        cursoMap.put("activo", curso.isActivo());
    
        // Obtener IDs de lecciones
        List<String> leccionIds = curso.getLecciones();
    
        // Recuperar documentos de lecciones
        List<Map<String, Object>> leccionesContenido = new ArrayList<>();

        boolean habilitarProximaLeccion = true;  // Usamos esto para controlar la habilitación de lecciones
    
        for (String leccionId : leccionIds) {
            Optional<Leccion> optionalLeccion = leccionService.findById(leccionId);
    
            if (optionalLeccion.isPresent()) {
                Leccion leccion = optionalLeccion.get();
    
                // Obtener mini lecciones
                List<Map<String, Object>> miniLecciones = new ArrayList<>();
    
                for (Object obj : leccion.getMiniLecciones()) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> miniLeccion = (Map<String, Object>) obj;
                        Map<String, Object> miniLeccionMap = new HashMap<>();
                        miniLeccionMap.put("mini_tema", miniLeccion.get("mini_tema"));
                        miniLeccionMap.put("tipo", miniLeccion.get("tipo"));
                        miniLeccionMap.put("contenido", miniLeccion.get("contenido"));
                        miniLeccionMap.put("video", miniLeccion.get("video"));
                        miniLeccionMap.put("pasos", miniLeccion.get("pasos"));
                        miniLecciones.add(miniLeccionMap);
                    }
                }
    
                // Obtener evaluaciones
                List<String> evaluacionIds = leccion.getEvaluaciones();
                if (evaluacionIds == null) {
                    evaluacionIds = new ArrayList<>();
                }
                List<Map<String, Object>> evaluaciones = new ArrayList<>();
                boolean evaluacionGanada = false; 
    
                for (String evaluacionId : evaluacionIds) {
                    Optional<Evaluacion> optionalEvaluacion = evaluacionService.findById(evaluacionId);
                    if (optionalEvaluacion.isPresent()) {
                        Evaluacion evaluacion = optionalEvaluacion.get();
                        Map<String, Object> evaluacionMap = new HashMap<>();
                        evaluacionMap.put("id", evaluacion.getId());
                        evaluacionMap.put("mini_tema", evaluacion.getTipo());
                        evaluacionMap.put("tipo", "quiz");
                        evaluacionMap.put("puntajeMaximo", evaluacion.getPuntajeMaximo());
                        evaluacionMap.put("intentosIlimitados", evaluacion.getIntentosIlimitados());
                        evaluacionMap.put("tiempoLimite", evaluacion.getTiempoLimite());
                        evaluacionMap.put("cuestionario", evaluacion.getCuestionario());
                        evaluacionMap.put("cuestionarioDificil", evaluacion.getCuestionarioDificil());
                    
    
                        // Buscar las evaluaciones realizadas por el estudiante y verificar si ha aprobado
                        Optional<EvaluacionEstudiante> evaluacionEstudianteOpt = evaluacionEstudianteService.findByUsuarioIdAndEvaluacionId( users.getId(), evaluacion.getId());
    
                        if (evaluacionEstudianteOpt.isPresent()) {
                            EvaluacionEstudiante evaluacionEstudiante = evaluacionEstudianteOpt.get();
                            if (evaluacionEstudiante.getPonderacion() > 60) {
                                evaluacionGanada = true; 
                            }
                        }

                        evaluacionMap.put("evaluacionGanada", evaluacionGanada);
    
                        evaluaciones.add(evaluacionMap);
                    }
                }
    
                // Crear el contenido de la lección
                Map<String, Object> leccionContenido = new HashMap<>();
                leccionContenido.put("titulo", leccion.getTitulo());
                leccionContenido.put("miniLecciones", miniLecciones);
                leccionContenido.put("evaluacion", evaluaciones);
    
                // Habilitar o deshabilitar la lección
                leccionContenido.put("deshabilitado", !habilitarProximaLeccion); // Solo habilitamos si la bandera es true
    
                // Si el estudiante ganó en la evaluación, habilitamos la siguiente lección
                if (evaluacionGanada) {
                    habilitarProximaLeccion = true;
                } else {
                    habilitarProximaLeccion = false; // La próxima lección estará deshabilitada si no aprobó la actual
                }
    
                // Agregar el contenido de la lección a la lista
                leccionesContenido.add(leccionContenido);
            }
        }
    
        // Crear la respuesta final

        cursoMap.put("lecciones_contenido", leccionesContenido);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("message", "Contenido del curso");
        response.put("data",  cursoMap);
    
        return ResponseEntity.ok(response);

    }


    @PostMapping("/reporte_curso")
    public ResponseEntity<?> getReporteCursos( @RequestBody Map<String, String> request ){

        Map<String, Object> response = new HashMap<>();

        String fechaInicio = request.get("fechaInicio");
        String fechaFin = request.get("fechaFin");


        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth.getPrincipal().toString();
        User users = userService.findByCorreo( correo );

    
        List<Curso> cursos = service.findByProfesorId( users.getId() );

        System.out.println( "---------------------------- " );
        System.out.println( " fechaInicio " + fechaInicio + " fehcafin" + fechaFin  );

        if (fechaInicio != null && fechaFin != null) {

            try {
                
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                Date inicio = isoFormat.parse(fechaInicio);
                Date fin = isoFormat.parse(fechaFin);

                System.out.println( "---------------------------- " );
                System.out.println( " fechaInicio " + fechaInicio + " fehcafin" + fechaFin  );
    
                // Buscar cursos por profesor y rango de fechas
                cursos = service.findByProfesorIdAndFechaCreacionBetween( users.getId(), inicio, fin );
    

            } catch (ParseException e) {
                response.put("valid", false);
                response.put("message", "Formato de fecha inválido");
                return ResponseEntity.badRequest().body(response);
            }

        } else {
            // Buscar cursos por el profesor si no hay rango de fechas
            cursos = service.findByProfesorId(users.getId());
        }

         // Convertir Curso a Map<String, Object>

         List<Map<String, Object>> ListadoCursos = new ArrayList<>();

         
         for( Curso curso: cursos ){
             
            Integer evaluciones_completadas_x_curso  = 0;
            
            Map<String, Object> cursoMapCustom = new HashMap<>();

            cursoMapCustom.put("nombre", curso.getNombre() );
            cursoMapCustom.put("tipoCurso", curso.getTipoCurso() );
            cursoMapCustom.put("estudiantes", curso.getEstudiantes() );
            cursoMapCustom.put("bloqueados", curso.getBloqueados() );
            cursoMapCustom.put("desasignados", curso.getDesasignados() );
            cursoMapCustom.put("completados", curso.getCompletados() );
            cursoMapCustom.put("fechaCreacion", curso.getFechaCreacion() );

            String id_curso = curso.getId();

            List<String> leccionesId = curso.getLecciones();

            for( String leccionId: leccionesId ){
            
                Optional<Leccion> leccion = leccionService.findById( leccionId );

                if( leccion.isPresent() ){

                    Leccion leccionObj = leccion.get();
                    List<String> evaluciones = leccionObj.getEvaluaciones();

                    // for( String evalucion: evaluciones  ){

                    //     Optional<EvaluacionEstudiante> evaluacionEstudiante = evaluacionEstudianteService.findByCursoIdAndEvaluacionId( id_curso, evalucion );

                    //     if( evaluacionEstudiante.isPresent() ){
                            
                    //         EvaluacionEstudiante eva_x_estudiante = evaluacionEstudiante.get();

                    //         if( eva_x_estudiante.getPonderacion() > 59 ){
                    //             evaluciones_completadas_x_curso += 1;
                    //         }
                            
                    //     }

                    // }

                      // Consulta MongoDB para obtener las evaluaciones completadas (ponderación > 59)
                        List<EvaluacionEstudiante> evaluacionesCompletadas = evaluacionEstudianteService.findByCursoIdAndEvaluacionIdInAndPonderacionGreaterThan(
                            id_curso,
                            evaluciones,
                            59
                        );

                        // Suma la cantidad de evaluaciones completadas
                        evaluciones_completadas_x_curso += evaluacionesCompletadas.size();
                
                }


            }

            cursoMapCustom.put("evaluciones_ganadas", evaluciones_completadas_x_curso);

            ListadoCursos.add( cursoMapCustom );

         }


        response.put("valid", true);
        response.put("message", "Lista de cursos");
        response.put("data", ListadoCursos );

        return ResponseEntity.ok(response);

    }
}