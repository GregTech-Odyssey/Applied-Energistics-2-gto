package dev.emi.emi.chess;

import java.util.List;

record ChessPiece(dev.emi.emi.chess.PieceType type, dev.emi.emi.chess.PieceColor color) {
	private static final List<ChessPiece> PIECES = List.of(
		new ChessPiece(dev.emi.emi.chess.PieceType.PAWN, dev.emi.emi.chess.PieceColor.WHITE),
		new ChessPiece(dev.emi.emi.chess.PieceType.ROOK, dev.emi.emi.chess.PieceColor.WHITE),
		new ChessPiece(dev.emi.emi.chess.PieceType.KNIGHT, dev.emi.emi.chess.PieceColor.WHITE),
		new ChessPiece(dev.emi.emi.chess.PieceType.BISHOP, dev.emi.emi.chess.PieceColor.WHITE),
		new ChessPiece(dev.emi.emi.chess.PieceType.QUEEN, dev.emi.emi.chess.PieceColor.WHITE),
		new ChessPiece(dev.emi.emi.chess.PieceType.KING, dev.emi.emi.chess.PieceColor.WHITE),
		new ChessPiece(dev.emi.emi.chess.PieceType.PAWN, dev.emi.emi.chess.PieceColor.BLACK),
		new ChessPiece(dev.emi.emi.chess.PieceType.ROOK, dev.emi.emi.chess.PieceColor.BLACK),
		new ChessPiece(dev.emi.emi.chess.PieceType.KNIGHT, dev.emi.emi.chess.PieceColor.BLACK),
		new ChessPiece(dev.emi.emi.chess.PieceType.BISHOP, dev.emi.emi.chess.PieceColor.BLACK),
		new ChessPiece(dev.emi.emi.chess.PieceType.QUEEN, dev.emi.emi.chess.PieceColor.BLACK),
		new ChessPiece(dev.emi.emi.chess.PieceType.KING, dev.emi.emi.chess.PieceColor.BLACK)
	);

	public static ChessPiece of(dev.emi.emi.chess.PieceType type, dev.emi.emi.chess.PieceColor color) {
		return PIECES.get(type.ordinal() + color.ordinal() * 6);
	}
}
