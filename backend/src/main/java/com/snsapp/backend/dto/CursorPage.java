package com.snsapp.backend.dto;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor) {
}
