package com.example.DataPreparationApp.Services.processors;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.File;

public interface FileProcessor {
    Dataset processFile(File file) throws Exception;
}