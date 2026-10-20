package dev.emi.emi.chess;

class NetworkedMoveGenerator extends MoveGenerator {
	public ChessMove move;

	public NetworkedMoveGenerator(dev.emi.emi.chess.PieceColor color) {
		super(color);
	}

	@Override
	public void ponderMove(dev.emi.emi.chess.ChessBoard board) {
		move = null;
	}

	@Override
	public boolean determinedMove() {
		return move != null;
	}

	@Override
	public ChessMove getMove() {
		return move;
	}
}
