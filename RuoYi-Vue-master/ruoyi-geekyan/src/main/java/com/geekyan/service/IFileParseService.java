package com.geekyan.service;

import java.util.Map;

public interface IFileParseService {
    Map<String, Object> parseFile(byte[] fileData, String fileName, String contentType);
}
