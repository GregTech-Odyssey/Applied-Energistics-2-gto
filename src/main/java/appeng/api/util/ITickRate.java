package appeng.api.util;

public interface ITickRate {

    int getDefaultMin();

    int getDefaultMax();

    int getMax();

    void setMax(int max);

    int getMin();

    void setMin(int min);

}
