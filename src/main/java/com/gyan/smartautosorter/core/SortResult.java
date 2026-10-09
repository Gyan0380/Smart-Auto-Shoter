package com.gyan.smartautosorter.core;

import java.util.List;

public final class SortResult {
    public record Move(int fromSlot, int toSlot, String itemId, int count, boolean merged) {}

    private final List<Move> moves;

    public SortResult(List<Move> moves) {
        this.moves = moves;
    }

    public List<Move> moves() {
        return moves;
    }

    public boolean isNoOp() {
        return moves.isEmpty();
    }

    public int moveCount() {
        return moves.size();
    }
}
