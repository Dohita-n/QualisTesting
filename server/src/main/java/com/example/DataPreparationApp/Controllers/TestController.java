package com.example.DataPreparationApp.Controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test controller to verify role-based access control.
 * This controller provides endpoints to test different permission levels.
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * Endpoint accessible to any authenticated user
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> userAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User Content");
        response.put("username", auth.getName());
        response.put("authorities", auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint requiring VIEW_DATA permission
     */
    @GetMapping("/view")
    @PreAuthorize("hasAuthority('VIEW_DATA')")
    public ResponseEntity<Map<String, String>> viewDataAccess() {
        return ResponseEntity.ok(Map.of("message", "VIEW_DATA Permission Content"));
    }

    /**
     * Endpoint requiring UPLOAD_DATA permission
     */
    @GetMapping("/upload")
    @PreAuthorize("hasAuthority('UPLOAD_DATA')")
    public ResponseEntity<Map<String, String>> uploadDataAccess() {
        return ResponseEntity.ok(Map.of("message", "UPLOAD_DATA Permission Content"));
    }

    /**
     * Endpoint requiring EDIT_DATA permission
     */
    @GetMapping("/edit")
    @PreAuthorize("hasAuthority('EDIT_DATA')")
    public ResponseEntity<Map<String, String>> editDataAccess() {
        return ResponseEntity.ok(Map.of("message", "EDIT_DATA Permission Content"));
    }

    /**
     * Endpoint requiring ADMIN authority
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, String>> adminAccess() {
        return ResponseEntity.ok(Map.of("message", "Admin Board"));
    }
} 