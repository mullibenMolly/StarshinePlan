package xx.world.blocks.production;

public interface voltageGraph {
    //使用该mod的电网必须的条件。
    float getRatePowerConsumption();//额定功率消耗，同时作为电网电力分配的比例系数

    float getMaxAcceptablePower();//最大可承受功率

    int getRateVoltageConsumption();//额定电压消耗

    int getMaxAcceptableVoltage();//最大可承受电压

    void powerOverload();//电力过载的方法，里面应该写对建筑的伤害。

    float getOverclockPowerConsumption();//超频功率，应该比额定功率大

    float getMaxOverclockEfficiency();//超频功率效率，输入功率中超出额定功率的那部分功率的效率。就比如额外输入一倍的功率，却只能提升0.5倍的效率。

}
