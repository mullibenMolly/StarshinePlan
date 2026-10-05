package xx;

import arc.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.gen.Building;
import xx.content.xx_UnitTypes;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import xx.content.xx_Blocks;
import xx.expand.EntityRegister;
import xx.expand.xx_HUD;
import xx.expand.F_CompositeUnitEntity;

import java.lang.reflect.Field;

public class xx extends Mod{


    private xx_HUD xx_HUD;

    static {
        EntityRegister.put(F_CompositeUnitEntity.class, F_CompositeUnitEntity::new);
    }

    @Override
    public void init() {
        // 分配 ID
        EntityRegister.load();
        xx_HUD = new xx_HUD();
        xx_HUD.build();

        Vars.renderer.minZoom = 0.01f;   // 默认 1.5，越小能缩越远
        Vars.renderer.maxZoom = 60f;    // 默认 15，越大能放越大



    }

    //指令，左右脑互博的产物，想要用就得开联机，但又只会在单机下使用
    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.register("mycmd", "指令集1.0", args -> {
            Log.info("执行了 mycmd");
        });

        handler.register("find", "寻找鼠标位置的建筑", args -> {
            float mouseX = Core.input.mouseWorldX();
            float mouseY = Core.input.mouseWorldY();
            Building build = Vars.world.buildWorld(mouseX, mouseY);
            if(build == null){
                Vars.ui.chatfrag.addMessage("[red]没有发现建筑！");
                return;
            }
            Class<? extends Building> buildClass = build.getClass();
            Field[] fields = buildClass.getDeclaredFields();
            for(Field f : fields){
                Vars.ui.chatfrag.addMessage(f.getName());
            }

        });

    }





    public xx(){
//        Log.info("Loaded ExampleJavaMod constructor.");
//
//        //listen for game load event
//        Events.on(ClientLoadEvent.class, e -> {
//            //show dialog upon startup
//            Time.runTask(10f, () -> {
//                BaseDialog dialog = new BaseDialog("frog");
//                dialog.cont.add("behold").row();
//                //mod sprites are prefixed with the mod name (this mod is called 'example-java-mod' in its config)
//                dialog.cont.image(Core.atlas.find("xx-java-mod-frog")).pad(20f).row();
//                dialog.cont.button("I see", dialog::hide).size(100f, 50f);
//                dialog.show();
//            });
//        });


        Events.run(Trigger.update, () -> {
            if (xx_HUD != null) {
                xx_HUD.update();
            }
        });






    }

    @Override
    public void loadContent(){
        Log.info("Loading some xx content.");
        xx_UnitTypes.load();
        EntityRegister.load();
        xx_Blocks.load();
    }


}
