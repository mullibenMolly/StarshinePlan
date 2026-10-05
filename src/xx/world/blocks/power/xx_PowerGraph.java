package xx.world.blocks.power;

import arc.math.Mathf;
import arc.math.WindowedMean;
import arc.struct.IntSet;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.gen.PowerGraphUpdater;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.consumers.ConsumePower;
import xx.world.blocks.production.voltageGraph;
import xx.world.blocks.production.voltageGraph_in;
import xx.world.blocks.production.voltageGraph_out;

import java.lang.reflect.Field;

public class xx_PowerGraph extends PowerGraph {
    public int graphVoltage;
    public float powerLoss;//不想管，出问题了再改成局部变量
    public float timer;//计时器
    public final float damageDelay = 3f;//伤害间隔，单位：帧
    public boolean getOff;//是否到达时间

    private static Field entityField;//缓存

    public final Seq<Building> powerNode = new Seq<>(false,16 , Building.class);//电力节点

    private static final Queue<Building> queue = new Queue<>();
    private static final Seq<Building> outArray1 = new Seq<>();
    private static final Seq<Building> outArray2 = new Seq<>();
    private static final IntSet closedSet = new IntSet();

    private final WindowedMean powerBalance = new WindowedMean(60);
    private float lastPowerProduced, lastPowerNeeded, lastPowerStored;
    private float lastScaledPowerIn, lastScaledPowerOut, lastCapacity;
    //diodes workaround for correct energy production info
    private float energyDelta = 0f;

    //运用反射
    static{
        try{
            entityField = PowerGraph.class.getDeclaredField("entity");
            entityField.setAccessible(true);

        }
        catch (Exception e) {
            Log.err("Failed to initialize reflection fields for xx_PowerGraph", e);
        }
    }

    //古法编程，你值得拥有

    public xx_PowerGraph(){
        super();
        initEntity();
    }

    public xx_PowerGraph(boolean noEntity){
        super();
    }

    @Override
    public float getSatisfaction(){
        if(Mathf.zero(lastPowerProduced)){
            return 0f;
        }else if(Mathf.zero(lastPowerNeeded)){
            return 1f;
        }
        return Mathf.clamp(lastPowerProduced / lastPowerNeeded);
    }//计算电力满意度，与电力节点的连接线缆的亮度有关


    private void initEntity() {
        try {
            PowerGraphUpdater entity = (PowerGraphUpdater) entityField.get(this);
            if (entity == null) {
                entity = PowerGraphUpdater.create();
                entityField.set(this, entity);
            }
            // 设置 entity.graph = this
            Field graphField = PowerGraphUpdater.class.getDeclaredField("graph");
            graphField.setAccessible(true);
            graphField.set(entity, this);
        } catch (Exception e) {
            Log.err("Failed to init xx_PowerGraph entity", e);
        }
    }

    @Override
    public float getPowerBalance(){
        return powerBalance.rawMean();
    }

    @Override
    public boolean hasPowerBalanceSamples(){
        return powerBalance.hasEnoughData();
    }

    @Override
    public float getLastPowerProduced(){
        return lastPowerProduced;
    }

    @Override
    public float getLastPowerNeeded(){
        return lastPowerNeeded;
    }

    //计算线损率
    public float getLineLossRate(){
        if(powerLoss == 0) return 0;
        return (float) Mathf.round(powerLoss / lastPowerProduced * 1000) / 10;
    }

    //计算电力节点电阻
    public float getSeriesResistance(){
        float resistance = 0;//临时存储
        var items = powerNode.items;
        for(int i = 0; i < powerNode.size; i++){//计算串联
            var seriesConnection = items[i];
            text_node2 powerNode = (text_node2) seriesConnection.block;
            resistance += powerNode.resistance;//remind 只有电力节点是串联
        }
        return resistance;
    }

    //计算损耗功率，线损功率加发电机阻抗
    public float getPowerLoss(){
        if(lastPowerProduced == 0) return 0;

        return Mathf.pow( lastPowerProduced/graphVoltage ,2) * getSeriesResistance();
    }

    @Override
    public void add(Building build){
        super.add(build);

        powerNode.clear();
        powerNode.addAll(all.select(item -> item != null && item.block instanceof text_node2));
    }

    @Override
    public void clear() {
        powerNode.clear();
        super.clear();
    }

    @Override
    public void removeList(Building build){
        all.remove(build);
        producers.remove(build);
        consumers.remove(build);
        batteries.remove(build);
        powerNode.remove(build);
    }

    @Override
    public void remove(Building tile){

        //go through all the connections of this tile
        for(Building other : tile.getPowerConnections(outArray1)){
            //a graph has already been assigned to this tile from a previous call, skip it
            if(other.power.graph != this) continue;

            xx_PowerGraph graph = new xx_PowerGraph();
            graph.checkAdd();
            graph.add(other);
            //add to queue for BFS
            queue.clear();
            queue.addLast(other);
            while(queue.size > 0){
                //get child from queue
                Building child = queue.removeFirst();
                //add it to the new branch graph
                graph.add(child);
                //go through connections
                for(Building next : child.getPowerConnections(outArray2)){
                    //make sure it hasn't looped back, and that the new graph being assigned hasn't already been assigned
                    //also skip closed tiles
                    if(next != tile && next.power.graph != graph){
                        graph.add(next);
                        queue.addLast(next);
                    }
                }
            }
            //update the graph once so direct consumers without any connected producer lose their power
            graph.update();
        }

        PowerGraphUpdater entity = null;
        try {
            entity = (PowerGraphUpdater) entityField.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        //implied empty graph here
        if(entity != null) entity.remove();
    }

    @Override//总产电功率，排除电压不符的
    public float getPowerProduced(){
        float powerProduced = 0f;
        var items = producers.items;
        for(int i = 0; i < producers.size; i++){

            var producer = items[i];

            voltageGraph_out v = (voltageGraph_out)producer;

            if(v.getMaxLoadPower() < lastPowerNeeded - lastPowerProduced){
                v.electricityCollapse();//电力崩溃
                Log.info(lastPowerNeeded);
            }
            else if(v.getOutputVoltage() >= graphVoltage) {
                powerProduced += producer.getPowerProduction();
            }//电力崩溃后排除该发电机

        }
        return powerProduced;
    }

    @Override//总额定耗电功率，排除电压不符的
    public float getPowerNeeded(){
        float powerNeeded = 0f;
        var items = consumers.items;

        if(getOff){
            for(int i = 0; i < consumers.size; i++){
                var consumer = items[i];
                voltageGraph_in v =  (voltageGraph_in) consumer;

                if(graphVoltage > v.getMaxAcceptableVoltage()){
                    v.voltageOverload();//电力过压，击穿保护，理应强制耗电
                    consumer.damagePierce(graphVoltage);
                }

                if(consumer.shouldConsumePower && v.getRateVoltageConsumption() <= graphVoltage){//TODO 这里电压判断也许应该放在shouldConsumePower里，注意上面还有
                    powerNeeded += consumer.block.consPower.requestedPower(consumer);
                }
            }
            return powerNeeded;
        }//只想做一次判断，但又懒得提取代码

        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            voltageGraph_in v =  (voltageGraph_in) consumer;

            if(graphVoltage > v.getMaxAcceptableVoltage()){
                v.voltageOverload();//电力过压，击穿保护，理应强制耗电
            }

            if(consumer.shouldConsumePower && v.getRateVoltageConsumption() <= graphVoltage){//TODO 这里电压判断也许应该放在shouldConsumePower里，注意上面还有
                powerNeeded += consumer.block.consPower.requestedPower(consumer);
            }
        }
        return powerNeeded;
    }

    //电网电压
    public int getGraphVoltage(){
        int voltage = 0;
        var items = producers.items;
        for(int i = 0; i < producers.size; i++){
            voltageGraph_out v =  (voltageGraph_out) items[i];
            voltage = Math.max( v.getOutputVoltage() , voltage );
        }
        return voltage;
    }

    //检测电网电力的功率，用于电力崩溃，电压不符的也会受到影响，所以分开来写
    public void examinePower(){
        var items = producers.items;
        for(int i = 0; i < producers.size; i++){
            voltageGraph_out v =  (voltageGraph_out) items[i];
            if(v.getMaxLoadPower() < lastPowerNeeded - lastPowerProduced){
                v.electricityCollapse();//电力过载
                Log.info(lastPowerNeeded);
            }

        }
    }

    @Override//电力分配
    public void distributePower(float needed, float produced, boolean charged) {
        var items = consumers.items;

        if(getOff){
            for (int i = 0; i < consumers.size; i++) {
                var consumer = items[i];
                voltageGraph_in v = (voltageGraph_in)consumer;//我实在不知道这该取什么名字。这作为附加属性
                ConsumePower consPower = consumer.block.consPower;
                if(graphVoltage >= v.getRateVoltageConsumption()) {//对比电网电压与机器额定电压
                    if (consumer.shouldConsumePower) {
                        float obtained = consPower.usage / needed * produced;//得到的电功率，分配得来的功率
                        consumer.power.status = Math.min(obtained / v.getRatePowerConsumption(), 1f);//计算电力满足度
                        //超频提供的额外效率
                        consumer.power.status += v.getMaxOverclockEfficiency() * (Math.min(obtained , v.getOverclockPowerConsumption()) / v.getRatePowerConsumption() - 1);
                        //分配电力过高时，过载
                        if(obtained > v.getMaxAcceptablePower()){
                            v.powerOverload();//电力过载
                            consumer.damagePierce(Math.max(consumer.health * 0.1f , obtained));
                        }
                    } else {
                        consumer.power.status = consPower.usage / (needed + consPower.usage) * produced;//机器未工作时，shouldConsumePower=false，这里是计算工作后，usage等于多少
                    }
                }
                else {
                    consumer.power.status = 0;//电压不匹配，直接=0
                }
            }
            return;
        }



            for (int i = 0; i < consumers.size; i++) {
                var consumer = items[i];

                voltageGraph_in v = (voltageGraph_in)consumer;//我实在不知道这该取什么名字。这作为附加属性

                ConsumePower consPower = consumer.block.consPower;


                if(graphVoltage >= v.getRateVoltageConsumption()) {//对比电网电压与机器额定电压

                    if (consumer.shouldConsumePower) {
                        float obtained = consPower.usage / needed * produced;//得到的电功率，分配得来的功率
                        consumer.power.status = Math.min(obtained / v.getRatePowerConsumption(), 1);//计算电力满足度
                        //超频提供的额外效率
                        consumer.power.status += v.getMaxOverclockEfficiency() * (Math.min(obtained , v.getOverclockPowerConsumption()) / v.getRatePowerConsumption() - 1);

                        //分配电力过高时，过载
                        if(obtained > v.getMaxAcceptablePower()){
                            v.powerOverload();//电力过载
                        }

                    } else {
                        consumer.power.status = consPower.usage / (needed + consPower.usage) * produced;//机器未工作时，shouldConsumePower=false，这里是计算工作后，usage等于多少
                    }

                }
                else {
                    consumer.power.status = 0;//电压不匹配，直接=0
                }
            }

    }

    @Override//电网每帧刷新
    public void update(){
        if(!consumers.isEmpty() && consumers.first().cheating()){
            //when cheating, just set status to 1
            for(Building tile : consumers){
                tile.power.status = 1f;
            }

            lastPowerNeeded = lastPowerProduced = 1f;
            return;
        }

        //Log.info("电网" + getID());
        timer = getOff?0:timer;
        timer += Time.delta;
        getOff = timer > damageDelay;

        float powerProduced = getPowerProduced();
        float powerNeeded = getPowerNeeded();


        powerLoss = getPowerLoss();
        lastPowerNeeded = powerNeeded + powerLoss;
        lastPowerProduced = powerProduced;
        graphVoltage = getGraphVoltage();//计算电网电压



        powerBalance.add(lastPowerProduced - lastPowerNeeded);//用于电力节点的bar

        if(!(consumers.size == 0 && producers.size == 0 && batteries.size == 0)){
            boolean charged = false;

            if(!Mathf.equal(powerNeeded, powerProduced)){
                if(powerNeeded > powerProduced){
                    float powerBatteryUsed = useBatteries(powerNeeded - powerProduced);
                    powerProduced += powerBatteryUsed;
                    lastPowerProduced += powerBatteryUsed;
                }else if(powerProduced > powerNeeded){
                    charged = true;
                    powerProduced -= chargeBatteries(powerProduced - powerNeeded);
                }
            }

            distributePower(powerNeeded, powerProduced - powerLoss, charged);
        }
    }


    @Override//调试内容
    public String toString(){
        float powerProduced = getPowerProduced();
        return "xx_PowerGraph{" +
                "\n产电producers = " + producers +
                "\n耗电consumers = " + consumers +
                "\n电池batteries = " + batteries +
                "\n传输 = "+ powerNode +
                "\n所有all = " + all +
                "\n个数all.size = "+all.size+
                "\ngraphID = " + getID() +
                "\n发电功率 = "+powerProduced+
                "\n损耗功率 = "+getPowerLoss()+
                "\n电网电压 = "+graphVoltage+
                "\n损耗电阻 = "+getSeriesResistance()+
                "\n功率损率 = "+getLineLossRate()+
                "\n}";
    }

}
