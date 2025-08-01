package com.example.DataPreparationApp.Events;

import com.example.DataPreparationApp.Model.File;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event triggered when a file virus scan is completed successfully
 */
@Getter
public class FileScanCompletedEvent extends ApplicationEvent {
    
    private final File file;
    
    public FileScanCompletedEvent(File file) {
        super(file);
        this.file = file;
    }
} 