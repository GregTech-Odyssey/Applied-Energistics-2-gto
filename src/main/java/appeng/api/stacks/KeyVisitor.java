package appeng.api.stacks;

@FunctionalInterface
public interface KeyVisitor {

    boolean visit(AEKey key, long amount);
}
