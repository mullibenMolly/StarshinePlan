package xx.world.blocks.production;

public interface voltageGraph_r extends voltageGraph {
    //用于线缆

    int getMaxVoltage();//最大电压

    float getMaxPower();//最大功率

    float getResistance();//电阻

}
