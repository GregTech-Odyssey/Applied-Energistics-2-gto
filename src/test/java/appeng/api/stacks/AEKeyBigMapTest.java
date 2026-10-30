package appeng.api.stacks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class AEKeyBigMapTest {
    @Test
    void accessesExistingValuesAndKeepsCopiesIndependent() {
        var key = mock(AEKey.class);
        var map = new AEKeyBigMap<AEKey>();

        assertEquals(5, map.addTo(key, 5));
        assertEquals(7, map.addTo(key, 7));
        assertEquals(12, map.getLongAmount(key));
        assertEquals(12, map.getAmount(key).toLong());
        map.set(key, 20);
        assertEquals(5, map.insert(key, 10, 25));

        var copy = map.clone();
        assertEquals(4, copy.extractLong(key, 4));
        assertEquals(21, copy.getLongAmount(key));
        assertEquals(25, map.getLongAmount(key));
        map.addAll(copy);
        assertEquals(46, map.getLongAmount(key));
        assertEquals(46, map.extractLong(key, 100));
        assertEquals(0, map.getLongAmount(key));
    }
}
