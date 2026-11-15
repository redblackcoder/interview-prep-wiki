package practice.prep.libs;

@FunctionalInterface
public interface Adder<T> {
    T add(T a, T b);

    @SuppressWarnings("unchecked")
    static <T> Adder<T> DEFAULT(Class<T> clazz) {
        if (Integer.class == clazz) {
            return (Adder<T>)(Adder<Integer>)Integer::sum;
        } else {
            return (a, b) -> {
                throw new UnsupportedOperationException("Add operation not supported for this type: " + clazz.getName());
            };
        }
    }
}
