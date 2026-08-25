package xx.world.blocks.power;

import arc.math.Mathf;
import arc.math.WindowedMean;
import arc.struct.IntSet;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.gen.Building;
import mindustry.gen.PowerGraphUpdater;
import mindustry.world.blocks.power.PowerGraph;
import xx.world.consumes.xx_ConsumePower;

import java.lang.reflect.Field;

public class xx_PowerGraph extends PowerGraph {//极具简化的电力系统，想要更加拟真，电脑会算冒烟的。这不是做电路模拟
    public int graphVoltage;//电压，这里指电压等级，如果真的用数值的话，我估计我会写死，玩家烦死，电脑算死
    public float powerLoss;

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

    //计算损耗功率，线损功率
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

    @Override//总产电功率
    public float getPowerProduced(){
        float powerProduced = 0f;
        var items = producers.items;
        for(int i = 0; i < producers.size; i++){
            var producer = items[i];
            powerProduced += producer.getPowerProduction() /* producer.delta()*/;
        }
        return powerProduced;
    }

    //总最小耗电功率
    public float getPowerMinNeeded(){
        float powerNeeded = 0f;
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            xx_ConsumePower consumePower = (xx_ConsumePower) consumer.block.consPower;
            if(consumer.shouldConsumePower && consumePower.ratedVoltage >= graphVoltage){//TODO 这里电压判断也许应该放在shouldConsumePower里，注意上面还有
                powerNeeded += consumePower.requestedMinPower(consumer);
            }
        }
        return powerNeeded;
    }

    @Override//总额定耗电功率
    public float getPowerNeeded(){
        float powerNeeded = 0f;
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            xx_ConsumePower consumePower = (xx_ConsumePower) consumer.block.consPower;
            if(consumer.shouldConsumePower && consumePower.ratedVoltage >= graphVoltage){//TODO 这里电压判断也许应该放在shouldConsumePower里，注意上面还有
                powerNeeded += consumePower.requestedPower(consumer);
            }
        }
        return powerNeeded;
    }

    //电网电压
    public int getGraphVoltage(){
        int voltage = 0;
        var items = producers.items;
        for(int i = 0; i < producers.size; i++){
            var producer = items[i];
            voltage = Math.max( ((xx_ConsumeGenerator.xx_ConsumeGeneratorBuild) producer).getProtentionVoltage() , voltage );
        }
        return voltage;
    }

    @Override//电力分配
    public void distributePower(float needed, float produced, boolean charged) {
        var items = consumers.items;

        float minNeeded = getPowerMinNeeded();
        //优先分情况，这里应该可以不用if，但我在想我这样弄是否可以在特定情况下节省点性能
        //这里应该可以优化的

        if (minNeeded <= produced && !Mathf.zero(produced)) {
            for (int i = 0; i < consumers.size; i++) {
                var consumer = items[i];

                xx_ConsumePower consPower = (xx_ConsumePower) consumer.block.consPower;//该电网只会存在这种电力消耗模块

                if (consumer.shouldConsumePower && graphVoltage >= consPower.ratedVoltage) {
                    float obtained = consPower.usage / needed * produced;//得到的电功率
                    float status = (obtained -  consPower.minUsage) / (consPower.usage - consPower.minUsage);//计算电力满足度
                    consumer.power.status = Math.min(status , 1);
                }
                else {
                    consumer.power.status =  produced >= (needed + consPower.minUsage)? 1 : 0 ;//机器未工作时，shouldConsumePower=false，这里是计算工作后，usage等于多少
                }

            }
        }
        else if(needed <= produced && !Mathf.zero(produced)){
            for (int i = 0; i < consumers.size; i++) {
                var consumer = items[i];

                xx_ConsumePower consPower = (xx_ConsumePower) consumer.block.consPower;//该电网只会存在这种电力消耗模块

                if (consumer.shouldConsumePower && graphVoltage >= consPower.ratedVoltage) {
                    consumer.power.status = 1;
                }
                else {
                    consumer.power.status =  produced >= (needed + consPower.usage)? 1 : 0 ;//机器未工作时，shouldConsumePower=false，这里是计算工作后，usage等于多少
                }

            }
        }
        else
        {
            for (int i = 0; i < consumers.size; i++) {
                var consumer = items[i];
                consumer.power.status = 0;
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


        float powerNeeded = getPowerNeeded();
        float powerProduced = getPowerProduced();

        //lineLossRate = getLineLossRate(powerProduced);

        //虽然不知道源码为什么怎么写，但怎么写一定有它的意义...对吧
        lastPowerNeeded = powerNeeded + powerLoss;
        lastPowerProduced = powerProduced;
        graphVoltage = getGraphVoltage();//计算电网电压
        powerLoss = getPowerLoss();


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
                "\n耗电功率 = "+ getPowerMinNeeded()+
                "\n损耗功率 = "+getPowerLoss()+
                "\n电网电压 = "+graphVoltage+
                "\n损耗电阻 = "+getSeriesResistance()+
                "\n功率损率 = "+getLineLossRate()+
                "\n}";
    }

}
