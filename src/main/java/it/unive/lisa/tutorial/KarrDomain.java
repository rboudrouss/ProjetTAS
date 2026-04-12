package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.BaseLattice;
import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.UnaryExpression;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.util.representation.MapRepresentation;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.*;
import java.util.function.Predicate;

/**
 * The Affine Equalities Domain (Karr's Domain).
 *
 * <p>
 * Tracks systems of affine linear equality constraints of the form:
 * {@code a_1*x_1 + a_2*x_2 + ... + a_n*x_n = c} at each program point.
 *
 * <p>
 * The abstract state is an affine subspace of Q^n represented by a constraint
 * matrix in Row-Reduced Echelon Form (RREF). The lattice ordering is:
 * <ul>
 * <li>BOTTOM: infeasible system (contradiction detected)</li>
 * <li>TOP: empty system (no constraints)</li>
 * <li>D1 ⊑ D2: every constraint of D2 is implied by D1 (D1 is more
 * constrained)</li>
 * </ul>
 *
 * <p>
 * The join (LUB) is the intersection of the row spaces of the two systems,
 * computed via the Zassenhaus algorithm.
 */
public class KarrDomain implements ValueDomain<KarrDomain>, BaseLattice<KarrDomain> {

    private static final double EPSILON = 1e-9;

    /**
     * Ordered list of variables tracked by this domain instance.
     * The i-th variable corresponds to column i in the constraint rows.
     */
    private final List<Identifier> variables;

    /**
     * Constraint rows in RREF. Each row has length {@code variables.size() + 1}.
     * Semantics: {@code row[0]*var[0] + ... + row[n-1]*var[n-1] + row[n] = 0}
     * where {@code row[n]} is the constant term.
     */
    private final List<double[]> rows;

    /** True iff this state is infeasible (BOTTOM). */
    private final boolean isBottomFlag;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Creates the TOP element (no constraints). */
    public KarrDomain() {
        this.variables = new ArrayList<>();
        this.rows = new ArrayList<>();
        this.isBottomFlag = false;
    }

    private KarrDomain(List<Identifier> variables, List<double[]> rows, boolean isBottom) {
        this.variables = new ArrayList<>(variables);
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        this.isBottomFlag = isBottom;
    }

    // =========================================================================
    // Numeric helpers
    // =========================================================================

    private static boolean isZero(double v) {
        return Math.abs(v) < EPSILON;
    }

    /** Returns true if any row in the list is a contradiction */
    private static boolean hasContradiction(List<double[]> mat, int numVars) {
        for (double[] row : mat) {
            boolean allVarZero = true;
            for (int i = 0; i < numVars; i++) {
                if (!isZero(row[i])) {
                    allVarZero = false;
                    break;
                }
            }
            if (allVarZero && !isZero(row[numVars]))
                return true;
        }
        return false;
    }

    /**
     * Brings {@code mat} to RREF in place, using only the first {@code pivotCols}
     * columns as potential pivot columns. {@code numCols} is the total row length
     * (including the constant column). Zero rows are removed.
     *
     * @return true if a contradiction was detected
     */
    private static boolean toRREF(List<double[]> mat, int numCols, int pivotCols) {
        int pivotRow = 0;
        for (int col = 0; col < pivotCols && pivotRow < mat.size(); col++) {
            // Find a row at or below pivotRow with non-zero entry in this column
            int found = -1;
            for (int r = pivotRow; r < mat.size(); r++) {
                if (!isZero(mat.get(r)[col])) {
                    found = r;
                    break;
                }
            }
            if (found < 0)
                continue;

            Collections.swap(mat, pivotRow, found);
            double[] pRow = mat.get(pivotRow);

            // Normalize: make the pivot entry 1
            double pVal = pRow[col];
            for (int c = 0; c < numCols; c++)
                pRow[c] /= pVal;

            // Eliminate this column from all other rows
            for (int r = 0; r < mat.size(); r++) {
                if (r == pivotRow)
                    continue;
                double factor = mat.get(r)[col];
                if (isZero(factor))
                    continue;
                double[] row = mat.get(r);
                for (int c = 0; c < numCols; c++)
                    row[c] -= factor * pRow[c];
            }
            pivotRow++;
        }

        // Remove all-zero rows
        mat.removeIf(row -> {
            for (double v : row)
                if (!isZero(v))
                    return false;
            return true;
        });

        return hasContradiction(mat, numCols - 1);
    }

    // =========================================================================
    // Variable set utilities
    // =========================================================================

    /**
     * Returns the union of two variable lists
     */
    private static List<Identifier> unifiedVars(List<Identifier> v1, List<Identifier> v2) {
        List<Identifier> unified = new ArrayList<>(v1);
        for (Identifier id : v2)
            if (!unified.contains(id))
                unified.add(id);
        return unified;
    }

    /**
     * Returns a copy of {@code rows} expanded to {@code newVars} (a superset of
     * {@code oldVars}).
     * New variables get coefficient 0 in all existing rows.
     */
    private static List<double[]> expandRows(
            List<double[]> rows, List<Identifier> oldVars, List<Identifier> newVars) {
        int oldN = oldVars.size();
        int newN = newVars.size();
        List<double[]> result = new ArrayList<>(rows.size());
        for (double[] row : rows) {
            double[] newRow = new double[newN + 1];
            for (int i = 0; i < oldN; i++) {
                int idx = newVars.indexOf(oldVars.get(i));
                newRow[idx] = row[i];
            }
            newRow[newN] = row[oldN]; // constant term
            result.add(newRow);
        }
        return result;
    }

    // =========================================================================
    // Row-space membership test
    // =========================================================================

    /**
     * Returns true iff {@code vec} (length {@code numCols}) is in the row space of
     * {@code rref} (already in RREF with variable pivots in columns 0..numCols-2).
     */
    private static boolean isInRowSpace(double[] vec, List<double[]> rref, int numCols) {
        double[] v = Arrays.copyOf(vec, numCols);
        for (double[] row : rref) {
            // Locate the pivot column of this RREF row (only variable columns)
            int pivotCol = -1;
            for (int c = 0; c < numCols - 1; c++) {
                if (!isZero(row[c])) {
                    pivotCol = c;
                    break;
                }
            }
            if (pivotCol < 0)
                continue;
            if (!isZero(v[pivotCol])) {
                // pivot is 1 in RREF, so factor = v[pivotCol]
                double factor = v[pivotCol];
                for (int c = 0; c < numCols; c++)
                    v[c] -= factor * row[c];
            }
        }
        for (double val : v)
            if (!isZero(val))
                return false;
        return true;
    }

    // =========================================================================
    // Zassenhaus row-space intersection (used for LUB)
    // =========================================================================

    /**
     * Computes a basis for the intersection of the row spaces of {@code M1} and
     * {@code M2} via the Zassenhaus algorithm. Both matrices have {@code numCols}
     * columns.
     *
     * <p>
     * Algorithm: form Z = [M1|M1; M2|0], row-reduce using only the LEFT half
     * as pivot columns, then collect rows whose left half became all-zero — their
     * RIGHT half spans R(M1) ∩ R(M2).
     */
    private static List<double[]> rowSpaceIntersection(
            List<double[]> M1, List<double[]> M2, int numCols) {
        // Build Z = [M1|M1; M2|0] (size (|M1|+|M2|) × 2*numCols)
        List<double[]> Z = new ArrayList<>();
        for (double[] row : M1) {
            double[] zRow = new double[2 * numCols];
            System.arraycopy(row, 0, zRow, 0, numCols);
            System.arraycopy(row, 0, zRow, numCols, numCols);
            Z.add(zRow);
        }
        for (double[] row : M2) {
            double[] zRow = new double[2 * numCols];
            System.arraycopy(row, 0, zRow, 0, numCols); // left = M2 row
            // right stays zero
            Z.add(zRow);
        }

        // Row-reduce using ONLY the left half (cols 0..numCols-1) as pivots
        int pivotRow = 0;
        for (int col = 0; col < numCols && pivotRow < Z.size(); col++) {
            int found = -1;
            for (int r = pivotRow; r < Z.size(); r++) {
                if (!isZero(Z.get(r)[col])) {
                    found = r;
                    break;
                }
            }
            if (found < 0)
                continue;

            Collections.swap(Z, pivotRow, found);
            double[] pRow = Z.get(pivotRow);
            double pVal = pRow[col];
            for (int c = 0; c < 2 * numCols; c++)
                pRow[c] /= pVal;

            for (int r = 0; r < Z.size(); r++) {
                if (r == pivotRow)
                    continue;
                double factor = Z.get(r)[col];
                if (isZero(factor))
                    continue;
                double[] row = Z.get(r);
                for (int c = 0; c < 2 * numCols; c++)
                    row[c] -= factor * pRow[c];
            }
            pivotRow++;
        }

        // Collect rows where the left half is all-zero; their right half ∈ R(M1)∩R(M2)
        List<double[]> intersection = new ArrayList<>();
        for (double[] zRow : Z) {
            boolean leftZero = true;
            for (int c = 0; c < numCols; c++) {
                if (!isZero(zRow[c])) {
                    leftZero = false;
                    break;
                }
            }
            if (!leftZero)
                continue;
            double[] rRow = Arrays.copyOfRange(zRow, numCols, 2 * numCols);
            boolean allZero = true;
            for (double v : rRow)
                if (!isZero(v)) {
                    allZero = false;
                    break;
                }
            if (!allZero)
                intersection.add(rRow);
        }
        return intersection;
    }

    // =========================================================================
    // Linear expression extraction
    // =========================================================================

    /** Represents an affine expression */
    private static class LinearExpr {
        final Map<Identifier, Double> coeffs;
        final double constant;

        LinearExpr(Map<Identifier, Double> coeffs, double constant) {
            this.coeffs = coeffs;
            this.constant = constant;
        }

        static LinearExpr ofConstant(double c) {
            return new LinearExpr(new HashMap<>(), c);
        }

        static LinearExpr ofVar(Identifier id) {
            Map<Identifier, Double> m = new HashMap<>();
            m.put(id, 1.0);
            return new LinearExpr(m, 0.0);
        }

        LinearExpr add(LinearExpr o) {
            Map<Identifier, Double> m = new HashMap<>(this.coeffs);
            for (Map.Entry<Identifier, Double> e : o.coeffs.entrySet())
                m.merge(e.getKey(), e.getValue(), Double::sum);
            return new LinearExpr(m, this.constant + o.constant);
        }

        LinearExpr scale(double k) {
            Map<Identifier, Double> m = new HashMap<>();
            for (Map.Entry<Identifier, Double> e : this.coeffs.entrySet())
                m.put(e.getKey(), e.getValue() * k);
            return new LinearExpr(m, this.constant * k);
        }

        LinearExpr sub(LinearExpr o) {
            return add(o.scale(-1.0));
        }

        boolean isConstant() {
            return coeffs.isEmpty();
        }
    }

    /**
     * Attempts to parse {@code expr} as a linear (affine) expression.
     * Returns {@code null} if the expression is non-linear or unrecognized.
     */
    private LinearExpr tryLinear(ValueExpression expr) {
        if (expr instanceof Identifier)
            return LinearExpr.ofVar((Identifier) expr);

        if (expr instanceof Constant) {
            Object val = ((Constant) expr).getValue();
            if (val instanceof Number)
                return LinearExpr.ofConstant(((Number) val).doubleValue());
            return null;
        }

        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            SymbolicExpression lSE = bin.getLeft(), rSE = bin.getRight();
            if (!(lSE instanceof ValueExpression) || !(rSE instanceof ValueExpression))
                return null;
            LinearExpr left = tryLinear((ValueExpression) lSE);
            LinearExpr right = tryLinear((ValueExpression) rSE);
            if (left == null || right == null)
                return null;

            if (bin.getOperator() instanceof AdditionOperator)
                return left.add(right);
            if (bin.getOperator() instanceof SubtractionOperator)
                return left.sub(right);
            if (bin.getOperator() instanceof MultiplicationOperator) {
                if (left.isConstant())
                    return right.scale(left.constant);
                if (right.isConstant())
                    return left.scale(right.constant);
                return null; // product of two non-constant expressions is non-linear
            }
            return null;
        }

        if (expr instanceof UnaryExpression) {
            UnaryExpression un = (UnaryExpression) expr;
            if (!(un.getOperator() instanceof NumericNegation))
                return null;
            SymbolicExpression innerSE = un.getExpression();
            if (!(innerSE instanceof ValueExpression))
                return null;
            LinearExpr inner = tryLinear((ValueExpression) innerSE);
            return inner == null ? null : inner.scale(-1.0);
        }

        return null;
    }

    // =========================================================================
    // Helper: add a linear constraint "expr = 0" to a row list
    // =========================================================================

    /**
     * Appends the constraint {@code expr = 0} as a new row to {@code targetRows},
     * using {@code varList} for column indices.
     * Returns false if {@code expr} references a variable not in {@code varList}.
     */
    private static boolean appendConstraintRow(
            LinearExpr expr, List<Identifier> varList, List<double[]> targetRows) {
        int n = varList.size();
        double[] row = new double[n + 1];
        for (Map.Entry<Identifier, Double> e : expr.coeffs.entrySet()) {
            int idx = varList.indexOf(e.getKey());
            if (idx < 0)
                return false;
            row[idx] = e.getValue();
        }
        row[n] = expr.constant;
        targetRows.add(row);
        return true;
    }

    // =========================================================================
    // ValueDomain interface
    // =========================================================================

    @Override
    public KarrDomain assign(
            Identifier id, ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        if (isBottom())
            return this;

        LinearExpr expr = tryLinear(expression);
        if (expr == null)
            // Non-linear assignment: project out id (lose all information about it)
            return forgetIdentifier(id);

        // Build the unified variable list: current vars + expression vars + id
        List<Identifier> newVars = new ArrayList<>(this.variables);
        for (Identifier v : expr.coeffs.keySet())
            if (!newVars.contains(v))
                newVars.add(v);
        if (!newVars.contains(id))
            newVars.add(id);

        int n = newVars.size();
        int idIdx = newVars.indexOf(id);

        // Expand existing rows to the new variable set
        List<double[]> newRows = expandRows(this.rows, this.variables, newVars);

        double a_i = expr.coeffs.getOrDefault(id, 0.0);

        if (!isZero(a_i)) {
            /*
             * Assignment involves id itself (e.g., id := id + 1).
             * We substitute old_id = (new_id - constant - sum_{j≠i} a_j*x_j) / a_i
             * into each existing constraint.
             *
             * For each row r: new_r_j = a_i*r_j - r_i*a_j (j ≠ idIdx)
             * new_r_i = r_i
             * new_r_const = a_i*r_const - r_i*a_0
             */
            for (int rowIdx = 0; rowIdx < newRows.size(); rowIdx++) {
                double[] r = newRows.get(rowIdx);
                double r_i = r[idIdx];
                double[] newRow = new double[n + 1];
                for (int j = 0; j < n; j++) {
                    if (j == idIdx) {
                        newRow[j] = r_i;
                    } else {
                        double a_j = expr.coeffs.getOrDefault(newVars.get(j), 0.0);
                        newRow[j] = a_i * r[j] - r_i * a_j;
                    }
                }
                newRow[n] = a_i * r[n] - r_i * expr.constant;
                newRows.set(rowIdx, newRow);
            }
        } else {
            /*
             * Assignment does NOT involve id (e.g., id := y + 1).
             * Step 1: project out id from existing constraints.
             * Step 2: add the new constraint id = expr.
             */
            int pivotRowIdx = -1;
            for (int r = 0; r < newRows.size(); r++) {
                if (!isZero(newRows.get(r)[idIdx])) {
                    pivotRowIdx = r;
                    break;
                }
            }

            if (pivotRowIdx >= 0) {
                double[] pivotRow = newRows.get(pivotRowIdx);
                double pivotVal = pivotRow[idIdx];
                List<double[]> projected = new ArrayList<>();
                for (int r = 0; r < newRows.size(); r++) {
                    if (r == pivotRowIdx)
                        continue; // drop the pivot row
                    double[] row = newRows.get(r);
                    if (!isZero(row[idIdx])) {
                        double factor = row[idIdx] / pivotVal;
                        double[] newRow = new double[n + 1];
                        for (int c = 0; c < n + 1; c++)
                            newRow[c] = row[c] - factor * pivotRow[c];
                        projected.add(newRow);
                    } else {
                        projected.add(Arrays.copyOf(row, n + 1));
                    }
                }
                newRows = projected;
            }

            // Add constraint id - expr = 0
            double[] newConstraint = new double[n + 1];
            newConstraint[idIdx] = 1.0;
            for (Map.Entry<Identifier, Double> e : expr.coeffs.entrySet()) {
                int j = newVars.indexOf(e.getKey());
                newConstraint[j] -= e.getValue();
            }
            newConstraint[n] = -expr.constant;
            newRows.add(newConstraint);
        }

        boolean contradiction = toRREF(newRows, n + 1, n);
        return new KarrDomain(newVars, newRows, contradiction);
    }

    @Override
    public KarrDomain smallStepSemantics(
            ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }

    @Override
    public KarrDomain assume(
            ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle)
            throws SemanticException {
        if (isBottom())
            return this;
        if (!(expression instanceof BinaryExpression))
            return this;

        BinaryExpression bin = (BinaryExpression) expression;
        if (!(bin.getOperator() instanceof ComparisonEq))
            return this;

        SymbolicExpression lSE = bin.getLeft(), rSE = bin.getRight();
        if (!(lSE instanceof ValueExpression) || !(rSE instanceof ValueExpression))
            return this;

        LinearExpr left = tryLinear((ValueExpression) lSE);
        LinearExpr right = tryLinear((ValueExpression) rSE);
        if (left == null || right == null)
            return this;

        // Add constraint left - right = 0
        LinearExpr diff = left.sub(right);

        List<Identifier> newVars = new ArrayList<>(this.variables);
        for (Identifier v : diff.coeffs.keySet())
            if (!newVars.contains(v))
                newVars.add(v);

        int n = newVars.size();
        List<double[]> newRows = expandRows(this.rows, this.variables, newVars);

        if (!appendConstraintRow(diff, newVars, newRows))
            return this;

        boolean contradiction = toRREF(newRows, n + 1, n);
        return new KarrDomain(newVars, newRows, contradiction);
    }

    @Override
    public KarrDomain forgetIdentifier(Identifier id) throws SemanticException {
        if (isBottom() || isTop() || !variables.contains(id))
            return this;

        int idIdx = variables.indexOf(id);
        int n = variables.size();

        // Gaussian elimination to project out the id column
        List<double[]> remaining = new ArrayList<>();
        double[] pivotRow = null;
        for (double[] row : rows) {
            if (!isZero(row[idIdx]) && pivotRow == null) {
                pivotRow = row; // use as pivot; do not add to remaining
            } else if (pivotRow != null && !isZero(row[idIdx])) {
                double factor = row[idIdx] / pivotRow[idIdx];
                double[] newRow = new double[n + 1];
                for (int c = 0; c < n + 1; c++)
                    newRow[c] = row[c] - factor * pivotRow[c];
                remaining.add(newRow);
            } else {
                remaining.add(Arrays.copyOf(row, n + 1));
            }
        }

        // Remove the id column from every remaining row
        List<Identifier> newVars = new ArrayList<>(variables);
        newVars.remove(id);
        int newN = newVars.size();
        List<double[]> compressed = new ArrayList<>(remaining.size());
        for (double[] row : remaining) {
            double[] newRow = new double[newN + 1];
            int dst = 0;
            for (int c = 0; c < n; c++) {
                if (c == idIdx)
                    continue;
                newRow[dst++] = row[c];
            }
            newRow[newN] = row[n];
            compressed.add(newRow);
        }

        toRREF(compressed, newN + 1, newN);
        return new KarrDomain(newVars, compressed, false);
    }

    @Override
    public KarrDomain forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
        KarrDomain result = this;
        for (Identifier id : new ArrayList<>(variables))
            if (test.test(id))
                result = result.forgetIdentifier(id);
        return result;
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        return variables.contains(id);
    }

    @Override
    public Satisfiability satisfies(
            ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        if (isBottom())
            return Satisfiability.BOTTOM;
        if (!(expression instanceof BinaryExpression))
            return Satisfiability.UNKNOWN;

        BinaryExpression bin = (BinaryExpression) expression;
        if (!(bin.getOperator() instanceof ComparisonEq))
            return Satisfiability.UNKNOWN;

        SymbolicExpression lSE = bin.getLeft(), rSE = bin.getRight();
        if (!(lSE instanceof ValueExpression) || !(rSE instanceof ValueExpression))
            return Satisfiability.UNKNOWN;

        LinearExpr left = tryLinear((ValueExpression) lSE);
        LinearExpr right = tryLinear((ValueExpression) rSE);
        if (left == null || right == null)
            return Satisfiability.UNKNOWN;

        LinearExpr diff = left.sub(right);

        // Build the row vector for "diff = 0" in the current variable space
        int n = variables.size();
        double[] row = new double[n + 1];
        for (Map.Entry<Identifier, Double> e : diff.coeffs.entrySet()) {
            int idx = variables.indexOf(e.getKey());
            if (idx < 0)
                return Satisfiability.UNKNOWN; // unknown variable
            row[idx] = e.getValue();
        }
        row[n] = diff.constant;

        return isInRowSpace(row, rows, n + 1)
                ? Satisfiability.SATISFIED
                : Satisfiability.UNKNOWN;
    }

    @Override
    public KarrDomain pushScope(ScopeToken token) throws SemanticException {
        return this;
    }

    @Override
    public KarrDomain popScope(ScopeToken token) throws SemanticException {
        return this;
    }

    // =========================================================================
    // Lattice interface
    // =========================================================================

    @Override
    public KarrDomain top() {
        return new KarrDomain(new ArrayList<>(), new ArrayList<>(), false);
    }

    @Override
    public KarrDomain bottom() {
        return new KarrDomain(new ArrayList<>(), new ArrayList<>(), true);
    }

    @Override
    public boolean isTop() {
        // No constraints = top (any variable value is possible)
        return !isBottomFlag && rows.isEmpty();
    }

    @Override
    public boolean isBottom() {
        return isBottomFlag;
    }

    /**
     * D1 ⊑ D2 iff every constraint of D2 is implied by D1's constraints.
     * (D1 is "more constrained" = represents fewer possible program states.)
     */
    @Override
    public boolean lessOrEqualAux(KarrDomain other) throws SemanticException {
        List<Identifier> unified = unifiedVars(this.variables, other.variables);
        int n = unified.size();

        List<double[]> thisE = expandRows(this.rows, this.variables, unified);
        List<double[]> otherE = expandRows(other.rows, other.variables, unified);
        toRREF(thisE, n + 1, n);

        for (double[] row : otherE)
            if (!isInRowSpace(row, thisE, n + 1))
                return false;
        return true;
    }

    /**
     * LUB = smallest affine subspace containing both.
     * Computed as the intersection of the row spaces via the Zassenhaus algorithm.
     */
    @Override
    public KarrDomain lubAux(KarrDomain other) throws SemanticException {
        List<Identifier> unified = unifiedVars(this.variables, other.variables);
        int n = unified.size();

        List<double[]> M1 = expandRows(this.rows, this.variables, unified);
        List<double[]> M2 = expandRows(other.rows, other.variables, unified);
        toRREF(M1, n + 1, n);
        toRREF(M2, n + 1, n);

        List<double[]> inter = rowSpaceIntersection(M1, M2, n + 1);
        toRREF(inter, n + 1, n);

        if (hasContradiction(inter, n))
            return new KarrDomain(unified, inter, true);
        return new KarrDomain(unified, inter, false);
    }

    /**
     * Karr's domain is of finite height over integers, so widening = LUB is sound.
     * (For general rationals a proper widening would be needed, but for typical
     * integer-valued programs this is sufficient.)
     */
    @Override
    public KarrDomain wideningAux(KarrDomain other) throws SemanticException {
        return lubAux(other);
    }

    /**
     * GLB = intersection of the two affine subspaces = conjunction of all
     * Computed by concatenating the constraint rows of both arguments and
     * normalizing.
     */
    @Override
    public KarrDomain glbAux(KarrDomain other) throws SemanticException {
        List<Identifier> unified = unifiedVars(this.variables, other.variables);
        int n = unified.size();

        List<double[]> merged = new ArrayList<>(expandRows(this.rows, this.variables, unified));
        merged.addAll(expandRows(other.rows, other.variables, unified));

        boolean contradiction = toRREF(merged, n + 1, n);
        return new KarrDomain(unified, merged, contradiction);
    }

    // =========================================================================
    // Representation
    // =========================================================================

    @Override
    public StructuredRepresentation representation() {
        if (isBottom())
            return Lattice.bottomRepresentation();
        if (isTop())
            return Lattice.topRepresentation();

        Map<StructuredRepresentation, StructuredRepresentation> map = new LinkedHashMap<>();
        int n = variables.size();
        for (int ri = 0; ri < rows.size(); ri++) {
            double[] row = rows.get(ri);
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < n; i++) {
                double c = row[i];
                if (isZero(c))
                    continue;
                if (!first)
                    sb.append(c > 0 ? " + " : " - ");
                else if (c < 0)
                    sb.append("-");
                double abs = Math.abs(c);
                if (!isZero(abs - 1.0))
                    sb.append(fmt(abs)).append("*");
                sb.append(variables.get(i).getName());
                first = false;
            }
            double cst = row[n];
            if (!isZero(cst)) {
                if (!first)
                    sb.append(cst > 0 ? " + " : " - ");
                else if (cst < 0)
                    sb.append("-");
                sb.append(fmt(Math.abs(cst)));
                first = false;
            }
            if (first)
                sb.append("0");
            sb.append(" = 0");
            map.put(new StringRepresentation("c" + ri),
                    new StringRepresentation(sb.toString()));
        }
        return new MapRepresentation(map);
    }

    private String fmt(double d) {
        long rounded = Math.round(d);
        return isZero(d - rounded) ? String.valueOf(rounded) : String.format("%.4f", d);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof KarrDomain))
            return false;
        KarrDomain other = (KarrDomain) o;
        if (isBottomFlag != other.isBottomFlag)
            return false;
        if (isBottomFlag)
            return true;

        List<Identifier> unified = unifiedVars(this.variables, other.variables);
        int n = unified.size();
        List<double[]> r1 = expandRows(this.rows, this.variables, unified);
        List<double[]> r2 = expandRows(other.rows, other.variables, unified);
        toRREF(r1, n + 1, n);
        toRREF(r2, n + 1, n);

        for (double[] row : r2)
            if (!isInRowSpace(row, r1, n + 1))
                return false;
        for (double[] row : r1)
            if (!isInRowSpace(row, r2, n + 1))
                return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isBottomFlag, variables.size(), rows.size());
    }

    @Override
    public String toString() {
        return representation().toString();
    }
}
