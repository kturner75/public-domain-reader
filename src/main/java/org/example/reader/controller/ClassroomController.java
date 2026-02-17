package org.example.reader.controller;

import org.example.reader.model.ClassroomContextResponse;
import org.example.reader.service.ClassroomContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classroom")
public class ClassroomController {

    private final ClassroomContextService classroomContextService;

    public ClassroomController(ClassroomContextService classroomContextService) {
        this.classroomContextService = classroomContextService;
    }

    @GetMapping("/context")
    public ClassroomContextResponse getContext() {
        return classroomContextService.getContext();
    }
}
