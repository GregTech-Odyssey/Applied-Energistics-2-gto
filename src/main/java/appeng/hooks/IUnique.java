package appeng.hooks;

import java.util.concurrent.atomic.AtomicInteger;

public interface IUnique {

    AtomicInteger ID = new AtomicInteger(1);

    int ae2$getUid();

}
