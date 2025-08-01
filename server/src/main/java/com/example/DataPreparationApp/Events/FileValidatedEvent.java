package com.example.DataPreparationApp.Events;

import com.example.DataPreparationApp.Model.File;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FileValidatedEvent extends ApplicationEvent {
    private final File file;

    public FileValidatedEvent(File file) {
        super(file);
        this.file = file;
    }
} 