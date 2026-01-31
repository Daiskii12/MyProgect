package org.example.dto.statistics;

import lombok.Data;

import javax.naming.directory.SearchResult;
import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchResult> data;
    private String error;
}