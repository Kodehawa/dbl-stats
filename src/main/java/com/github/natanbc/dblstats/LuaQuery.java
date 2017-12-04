package com.github.natanbc.dblstats;

import com.github.natanbc.discordbotsapi.BotInfo;
import com.github.natanbc.luaeval.LuaEvaluator;
import com.github.natanbc.luaeval.utils.LuaObject;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LuaQuery {
    @SuppressWarnings("unchecked")
    public static List<BotInfo> query(List<BotInfo> original, String code) {
        try {
            LuaValue v = createEvaluator().set("bots", original.stream()).eval(code);
            if(v instanceof LuaObject) {
                Object o = ((LuaObject) v).getJavaObject();
                if(o instanceof Stream) o = ((Stream)o).collect(Collectors.toList());
                if(o instanceof List) {
                    List l = (List)o;
                    if(l.size() > 0 && !l.stream().allMatch(e->e instanceof BotInfo)) {
                        throw new InvalidReturnException("Non bot value returned");
                    }
                    return (List<BotInfo>)l;
                } else {
                    throw new InvalidReturnException("Return value not a list or stream");
                }
            }
            throw new InvalidReturnException(v.tojstring());
        } catch(NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    public static class InvalidReturnException extends RuntimeException {
        InvalidReturnException(String msg) {
            super(msg);
        }
    }

    private static LuaEvaluator createEvaluator() throws NoSuchMethodException {
        LuaEvaluator e = new LuaEvaluator(new TemporaryClassLoader(), 20000)
                .remove("io")
                .remove("luajava")
                .remove("os")
                .remove("package")
                .remove("coroutine")
                .remove("Java")
                .remove("require")
                .remove("print")
                .remove("dofile")
                .remove("loadfile")
                .remove("collectgarbage")
                .remove("rawset")
                .remove("rawget")
                .blockMethod(Object.class.getMethod("getClass"));
        LuaValue l = e.getGlobals().get("load");
        return e.set("load", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue code = args.arg(1).checkstring();
                LuaValue name = args.arg(2);
                if(!name.isnil()) name.checkstring();
                LuaValue mode = args.arg(3);
                if(!mode.isnil()) {
                    switch(mode.checkjstring()) {
                        case "b":
                        case "bt":
                            LuaValue.error("Loading binary chunks is disabled");
                        default:
                            if(!mode.tojstring().equals("b")) {
                                LuaValue.error("Unknown load mode " + mode);
                            }
                    }
                }
                LuaValue env = args.arg(4);
                if(!env.isnil()) env.checktable();
                return l.invoke(new LuaValue[]{code, name, LuaString.valueOf("t"), env});
            }
        });
    }

    private static class TemporaryClassLoader extends ClassLoader {}
}
