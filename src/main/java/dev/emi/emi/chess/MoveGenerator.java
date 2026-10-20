package dev.emi.emi.chess;

import java.util.Random;

abstract class MoveGenerator {
	public static final Random RANDOM = new Random();
	public dev.emi.emi.chess.PieceColor color;
	
	public MoveGenerator(dev.emi.emi.chess.PieceColor color) {
		this.color = color;
	}

	public abstract void ponderMove(dev.emi.emi.chess.ChessBoard board);

	public abstract boolean determinedMove();

	public abstract ChessMove getMove();
}
