package xx.world.blocks.production;

import mindustry.gen.Building;

public interface voltageGraph_in extends voltageGraph {
    //使用该mod的电网必须的条件。
    float getRatePowerConsumption();//额定功率消耗，同时作为电网电力分配的比例系数

//    float getMaxAcceptablePower();

//    float getMaxAcceptableCurrent();//最大电流

    boolean isPowerOverload(float power);//判断是否电流过载，并传输数值，这个要每帧调用

    boolean isVoltageOverload(int voltage);//判断是否电压过载

    //过载机制还是很谜，调用过载方法是间隔的，但判断是每帧的。算了，能跑就行了。
    //在电网中，先检测电压，再检测电流

    int getRateVoltageConsumption();//额定电压消耗

//    int getMaxAcceptableVoltage();//最大可承受电压

    void voltageOverload();//电压过压，强制工作，导致空转

    void powerOverload();//电力过载的方法，里面应该写对建筑的伤害。

    float getOverclockPowerConsumption();//超频功率，应该比额定功率大，最大功率小

    float getMaxOverclockEfficiency();//超频功率效率，输入功率中超出额定功率的那部分功率的效率。就比如额外输入一倍的功率，却只能提升0.5倍的效率。
}
