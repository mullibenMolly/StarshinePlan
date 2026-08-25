package xx.world.consumes;

import mindustry.gen.Building;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.Stats;
import xx.world.meta.xx_Stat;
import xx.world.meta.xx_StatUnit;

public class xx_ConsumePower extends ConsumePower {

        //remind usage就是需求功率，额定功率，这里的额定是最小功率，低于它将不工作
        //remind maxUsage是block的字段
        public float minUsage;//最小功率，用于机器不满效运行

        public int ratedVoltage = 1;//标准电压等级，可以高不能低，这是工作门槛

//        public float ratedCurrent = 1;//标准电流
//        public float minCurrent = 1;//最小电流
        //最大电流在block里，是机器本身的性质



        //电力限制关于电压于电流，效率限制关于功率,感觉可以弄一个新的类

        public float maxDischargeCurrent;//最大放电电流，这是用于电池的
        public float maxDischargePower;//放电功率

        //TODO有关电池的设置以后来弄。

        //默认
        public xx_ConsumePower(){
                super();
        }

        public xx_ConsumePower(float usage, float capacity, boolean buffered){
                this.usage = usage;
                this.capacity = capacity;
                this.buffered = buffered;
        }

        //remind 这个才有用
        public xx_ConsumePower(float usage , int ratedVoltage){
                this(usage , 0 , false);
                this.ratedVoltage = ratedVoltage;
                this.minUsage = usage;
        }

        //提供最小功率设置
        public xx_ConsumePower(float usage , float minUsage, int ratedVoltage){
                this(usage , ratedVoltage);
                this.minUsage = minUsage;
        }

        @Override
        public void display(Stats stats){
                if(usage > 0f){
                        stats.add(Stat.powerUse, usage, xx_StatUnit.powerSecond2);//额定功率
                        stats.add(xx_Stat.minPowerUse, minUsage, xx_StatUnit.powerSecond2);//最小功率
                        stats.add(xx_Stat.ratedVoltage, ratedVoltage, xx_StatUnit.voltage);//额定电压
                }
        }

        //最小功率
        public float requestedMinPower(Building entity){
                return minUsage * (entity.shouldConsume() ? 1f : 0f);
        }

        @Override//额定功率
        public float requestedPower(Building entity){
                return usage * (entity.shouldConsume() ? 1f : 0f);
        }
}
