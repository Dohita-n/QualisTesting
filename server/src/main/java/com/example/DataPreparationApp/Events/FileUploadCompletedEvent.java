package com.example.DataPreparationApp.Events;

import com.example.DataPreparationApp.Model.File;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event triggered when a file upload is completed
 */
@Getter
public class FileUploadCompletedEvent extends ApplicationEvent {
    
    private final File file;
    
    public FileUploadCompletedEvent(File file) {
        super(file);
        this.file = file;
    }
} 