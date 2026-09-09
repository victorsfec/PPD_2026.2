package br.com.victorsfec.fanorona.game;

/** Identidade das peças; zero representa uma interseção vazia. */
public enum Piece {
    EMPTY(0), WHITE(1), BLACK(2);
    public final int id;
    Piece(int id) { this.id = id; }
}
