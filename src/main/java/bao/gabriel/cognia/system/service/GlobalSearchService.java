package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.search.SearchResponse;

public interface GlobalSearchService {

    SearchResponse search(String keyword, Long userId);
}
