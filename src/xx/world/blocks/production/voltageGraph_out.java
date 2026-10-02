package xx.world.blocks.production;

public interface voltageGraph_out extends voltageGraph{

    float getMaxLoadPower();//最大负载功率，这个应该大于输出功率，与电力崩溃相关

    int getOutputVoltage();//电压输出

    void electricityCollapse();//电力崩溃，当电网需求功率过高时引发崩溃
}
