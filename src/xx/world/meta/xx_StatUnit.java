package xx.world.meta;

import mindustry.gen.Iconc;
import mindustry.world.meta.StatUnit;

public class xx_StatUnit extends StatUnit{
    public xx_StatUnit(String name) {
        super(name);
    }

    //核心数据库，信息显示
    public static final StatUnit
            powerSecond2 = new StatUnit("powerSecond2", "[accent]" + Iconc.power + "[]"),
            voltage = new StatUnit("voltage","[red]" + Iconc.power + "[]");

}
