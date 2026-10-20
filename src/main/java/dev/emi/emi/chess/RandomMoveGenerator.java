package dev.emi.emi.chess;

import java.util.List;

class RandomMoveGenerator extends dev.emi.emi.chess.MoveGenerator {
	private ChessMove move;

	public RandomMoveGenerator(dev.emi.emi.chess.PieceColor color) {
		super(color);
	}

	@Override
	public void ponderMove(dev.emi.emi.chess.ChessBoard board) {
		List<ChessMove> moves = board.getLegal(board.getAllMoves(color));
		if (moves.size() > 0) {
			move = moves.get(RANDOM.nextInt(moves.size()));
		} else {
			move = null;
		}
	}

	@Override
	public boolean determinedMove() {
		return true;
	}

	@Override
	public ChessMove getMove() {
		return move;
	}
}
