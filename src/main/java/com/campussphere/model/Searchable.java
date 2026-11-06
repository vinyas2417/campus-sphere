package com.campussphere.model;

import java.util.List;

public interface Searchable<T> {
    List<T> search(String query);
}
