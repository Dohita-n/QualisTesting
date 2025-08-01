package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.DTO.RoleDTO;
import com.example.DataPreparationApp.Model.Role;
import com.example.DataPreparationApp.Repository.RoleRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleService {
    
    private final RoleRepository roleRepository;
    
    @Autowired
    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }
    
    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(RoleDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public RoleDTO getRoleById(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));
        return RoleDTO.fromEntity(role);
    }
    
    public RoleDTO getRoleByName(String name) {
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with name: " + name));
        return RoleDTO.fromEntity(role);
    }
    
    @Transactional
    public RoleDTO createRole(RoleDTO roleDTO) {
        if (roleRepository.existsByName(roleDTO.getName())) {
            throw new EntityExistsException("Role with name " + roleDTO.getName() + " already exists");
        }
        
        Role role = roleDTO.toEntity();
        Role savedRole = roleRepository.save(role);
        return RoleDTO.fromEntity(savedRole);
    }
    
    @Transactional
    public RoleDTO updateRole(UUID id, RoleDTO roleDTO) {
        Role existingRole = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));
        
        // Check if new name is already taken by another role
        if (!existingRole.getName().equals(roleDTO.getName()) && 
                roleRepository.existsByName(roleDTO.getName())) {
            throw new EntityExistsException("Role with name " + roleDTO.getName() + " already exists");
        }
        
        existingRole.setName(roleDTO.getName());
        existingRole.setDescription(roleDTO.getDescription());
        
        Role updatedRole = roleRepository.save(existingRole);
        return RoleDTO.fromEntity(updatedRole);
    }
    
    @Transactional
    public void deleteRole(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new EntityNotFoundException("Role not found with id: " + id);
        }
        roleRepository.deleteById(id);
    }
    
    //Add a role if it doesnt exist, endpoints: /api/roles/create-if-not-exists
    @Transactional
    public RoleDTO createIfNotExists(String name, String description) {
        return roleRepository.findByName(name)
                .map(RoleDTO::fromEntity)
                .orElseGet(() -> {
                    RoleDTO roleDTO = new RoleDTO();
                    roleDTO.setName(name);
                    roleDTO.setDescription(description);
                    return createRole(roleDTO);
                });
    }
} 