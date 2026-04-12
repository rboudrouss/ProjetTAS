package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;

public class CartesianProductSetFloatKarr
        extends ValueCartesianProduct<ValueEnvironment<SetOfFloatValuesWithOverflow>, KarrDomain> {

    public CartesianProductSetFloatKarr(
            ValueEnvironment<SetOfFloatValuesWithOverflow> left,
            KarrDomain right) {
        super(left, right);
    }

    @Override
    public CartesianProductSetFloatKarr mk(
            ValueEnvironment<SetOfFloatValuesWithOverflow> left,
            KarrDomain right) {
        return new CartesianProductSetFloatKarr(left, right);
    }
}
