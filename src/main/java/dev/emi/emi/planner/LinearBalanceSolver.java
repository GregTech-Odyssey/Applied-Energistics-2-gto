package dev.emi.emi.planner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LinearBalanceSolver {
	private static final double EPSILON = 1.0E-10D;
	private static final double FEASIBILITY_EPSILON = 1.0E-7D;
	private static final int MAX_ITERATIONS = 20000;

	private LinearBalanceSolver() {
	}

	public static Result minimizeEqualities(double[][] coefficients, double[] rhs, double[] objective) {
		if (coefficients == null || rhs == null || objective == null) {
			return Result.invalid(objective == null ? 0 : objective.length);
		}
		int variables = objective.length;
		if (rhs.length != coefficients.length) {
			return Result.invalid(variables);
		}
		for (double value : objective) {
			if (!Double.isFinite(value)) {
				return Result.invalid(variables);
			}
		}

		List<double[]> rows = new ArrayList<>();
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < coefficients.length; i++) {
			double[] source = coefficients[i];
			if (source == null || source.length != variables || !Double.isFinite(rhs[i])) {
				return Result.invalid(variables);
			}
			double scale = 0.0D;
			for (double coefficient : source) {
				if (!Double.isFinite(coefficient)) {
					return Result.invalid(variables);
				}
				scale = Math.max(scale, Math.abs(coefficient));
			}
			if (scale <= EPSILON) {
				if (Math.abs(rhs[i]) > FEASIBILITY_EPSILON) {
					return Result.infeasible(variables);
				}
				continue;
			}
			double[] normalized = Arrays.copyOf(source, source.length);
			double value = rhs[i];
			if (value < 0.0D) {
				value = -value;
				for (int j = 0; j < normalized.length; j++) {
					normalized[j] = -normalized[j];
				}
			}
			rows.add(normalized);
			values.add(value);
		}

		if (rows.isEmpty()) {
			return new Result(Status.OPTIMAL, new double[variables], 0.0D);
		}

		int rowCount = rows.size();
		int totalVariables = variables + rowCount;
		double[][] tableau = new double[rowCount][totalVariables];
		double[] bounds = new double[rowCount];
		int[] basis = new int[rowCount];
		for (int i = 0; i < rowCount; i++) {
			System.arraycopy(rows.get(i), 0, tableau[i], 0, variables);
			tableau[i][variables + i] = 1.0D;
			bounds[i] = values.get(i);
			basis[i] = variables + i;
		}

		double[] phaseOneObjective = new double[totalVariables];
		for (int i = variables; i < totalVariables; i++) {
			phaseOneObjective[i] = -1.0D;
		}
		SimplexStatus phaseOne = optimize(tableau, bounds, basis, phaseOneObjective, totalVariables);
		if (phaseOne != SimplexStatus.OPTIMAL) {
			return phaseOne == SimplexStatus.UNBOUNDED ? Result.unbounded(variables) : Result.invalid(variables);
		}
		double phaseOneValue = objectiveValue(bounds, basis, phaseOneObjective);
		if (phaseOneValue < -FEASIBILITY_EPSILON) {
			return Result.infeasible(variables);
		}

		boolean[] redundant = new boolean[rowCount];
		boolean[] basic = new boolean[totalVariables];
		for (int index : basis) {
			if (index >= 0 && index < basic.length) {
				basic[index] = true;
			}
		}
		for (int row = 0; row < rowCount; row++) {
			if (basis[row] < variables) {
				continue;
			}
			int entering = -1;
			for (int column = 0; column < variables; column++) {
				if (!basic[column] && Math.abs(tableau[row][column]) > EPSILON) {
					entering = column;
					break;
				}
			}
			if (entering >= 0) {
				basic[basis[row]] = false;
				pivot(tableau, bounds, basis, row, entering);
				basic[entering] = true;
			} else if (Math.abs(bounds[row]) <= FEASIBILITY_EPSILON) {
				redundant[row] = true;
			} else {
				return Result.infeasible(variables);
			}
		}

		int retainedRows = 0;
		for (boolean value : redundant) {
			if (!value) {
				retainedRows++;
			}
		}
		if (retainedRows != rowCount) {
			double[][] compactTableau = new double[retainedRows][totalVariables];
			double[] compactBounds = new double[retainedRows];
			int[] compactBasis = new int[retainedRows];
			int next = 0;
			for (int i = 0; i < rowCount; i++) {
				if (redundant[i]) {
					continue;
				}
				compactTableau[next] = Arrays.copyOf(tableau[i], totalVariables);
				compactBounds[next] = bounds[i];
				compactBasis[next] = basis[i];
				next++;
			}
			tableau = compactTableau;
			bounds = compactBounds;
			basis = compactBasis;
		}

		double[] phaseTwoObjective = new double[totalVariables];
		for (int i = 0; i < variables; i++) {
			phaseTwoObjective[i] = -objective[i];
		}
		SimplexStatus phaseTwo = optimize(tableau, bounds, basis, phaseTwoObjective, variables);
		if (phaseTwo == SimplexStatus.UNBOUNDED) {
			return Result.unbounded(variables);
		}
		if (phaseTwo != SimplexStatus.OPTIMAL) {
			return Result.invalid(variables);
		}

		double[] solution = new double[variables];
		for (int row = 0; row < basis.length; row++) {
			int variable = basis[row];
			if (variable >= 0 && variable < variables) {
				solution[variable] = Math.max(0.0D, bounds[row]);
			}
		}
		double value = 0.0D;
		for (int i = 0; i < variables; i++) {
			value += objective[i] * solution[i];
		}
		return new Result(Status.OPTIMAL, solution, value);
	}

	private static SimplexStatus optimize(double[][] tableau, double[] bounds, int[] basis, double[] objective, int enteringLimit) {
		for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
			int entering = chooseEntering(tableau, basis, objective, enteringLimit);
			if (entering < 0) {
				return SimplexStatus.OPTIMAL;
			}
			int leaving = chooseLeaving(tableau, bounds, basis, entering);
			if (leaving < 0) {
				return SimplexStatus.UNBOUNDED;
			}
			pivot(tableau, bounds, basis, leaving, entering);
		}
		return SimplexStatus.ITERATION_LIMIT;
	}

	private static int chooseEntering(double[][] tableau, int[] basis, double[] objective, int enteringLimit) {
		boolean[] basic = new boolean[objective.length];
		for (int variable : basis) {
			if (variable >= 0 && variable < basic.length) {
				basic[variable] = true;
			}
		}
		int limit = Math.min(enteringLimit, objective.length);
		for (int column = 0; column < limit; column++) {
			if (basic[column]) {
				continue;
			}
			double reduced = objective[column];
			for (int row = 0; row < basis.length; row++) {
				int basicVariable = basis[row];
				reduced -= objective[basicVariable] * tableau[row][column];
			}
			if (reduced > EPSILON) {
				return column;
			}
		}
		return -1;
	}

	private static int chooseLeaving(double[][] tableau, double[] bounds, int[] basis, int entering) {
		int leaving = -1;
		double bestRatio = Double.POSITIVE_INFINITY;
		for (int row = 0; row < tableau.length; row++) {
			double coefficient = tableau[row][entering];
			if (coefficient <= EPSILON) {
				continue;
			}
			double ratio = bounds[row] / coefficient;
			if (ratio < bestRatio - EPSILON) {
				bestRatio = ratio;
				leaving = row;
			} else if (Math.abs(ratio - bestRatio) <= EPSILON && leaving >= 0 && basis[row] < basis[leaving]) {
				leaving = row;
			}
		}
		return leaving;
	}

	private static void pivot(double[][] tableau, double[] bounds, int[] basis, int pivotRow, int pivotColumn) {
		double pivot = tableau[pivotRow][pivotColumn];
		for (int column = 0; column < tableau[pivotRow].length; column++) {
			tableau[pivotRow][column] /= pivot;
			if (Math.abs(tableau[pivotRow][column]) < EPSILON) {
				tableau[pivotRow][column] = 0.0D;
			}
		}
		bounds[pivotRow] /= pivot;
		if (Math.abs(bounds[pivotRow]) < EPSILON) {
			bounds[pivotRow] = 0.0D;
		}
		for (int row = 0; row < tableau.length; row++) {
			if (row == pivotRow) {
				continue;
			}
			double factor = tableau[row][pivotColumn];
			if (Math.abs(factor) <= EPSILON) {
				continue;
			}
			for (int column = 0; column < tableau[row].length; column++) {
				tableau[row][column] -= factor * tableau[pivotRow][column];
				if (Math.abs(tableau[row][column]) < EPSILON) {
					tableau[row][column] = 0.0D;
				}
			}
			bounds[row] -= factor * bounds[pivotRow];
			if (Math.abs(bounds[row]) < EPSILON) {
				bounds[row] = 0.0D;
			}
		}
		basis[pivotRow] = pivotColumn;
	}

	private static double objectiveValue(double[] bounds, int[] basis, double[] objective) {
		double value = 0.0D;
		for (int row = 0; row < basis.length; row++) {
			value += objective[basis[row]] * bounds[row];
		}
		return value;
	}

	private enum SimplexStatus {
		OPTIMAL,
		UNBOUNDED,
		ITERATION_LIMIT
	}

	public enum Status {
		OPTIMAL,
		INFEASIBLE,
		UNBOUNDED,
		INVALID
	}

	public record Result(Status status, double[] solution, double objective) {
		public boolean solved() {
			return status == Status.OPTIMAL;
		}

		private static Result infeasible(int variables) {
			return new Result(Status.INFEASIBLE, new double[Math.max(0, variables)], Double.NaN);
		}

		private static Result unbounded(int variables) {
			return new Result(Status.UNBOUNDED, new double[Math.max(0, variables)], Double.NaN);
		}

		private static Result invalid(int variables) {
			return new Result(Status.INVALID, new double[Math.max(0, variables)], Double.NaN);
		}
	}
}
