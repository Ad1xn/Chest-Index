package dev.adrian.chestindex.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

//? if >=26.3 {
/*import org.lwjgl.sdl.SDLMouse;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}

public final class Keys {
    private Keys() {}

    //? if >=26.3 {
    /*public static final int GRAVE = InputConstants.KEY_GRAVE;
    public static final int Z = InputConstants.KEY_Z;
    public static final int ENTER = InputConstants.KEY_RETURN;
    public static final int KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int TAB = InputConstants.KEY_TAB;
    public static final int UP = InputConstants.KEY_UP;
    public static final int DOWN = InputConstants.KEY_DOWN;
    public static final int ESCAPE = InputConstants.KEY_ESCAPE;
    *///?} else {
    public static final int GRAVE = GLFW.GLFW_KEY_GRAVE_ACCENT;
    public static final int Z = GLFW.GLFW_KEY_Z;
    public static final int ENTER = GLFW.GLFW_KEY_ENTER;
    public static final int KP_ENTER = GLFW.GLFW_KEY_KP_ENTER;
    public static final int TAB = GLFW.GLFW_KEY_TAB;
    public static final int UP = GLFW.GLFW_KEY_UP;
    public static final int DOWN = GLFW.GLFW_KEY_DOWN;
    public static final int ESCAPE = GLFW.GLFW_KEY_ESCAPE;
    //?}

    //? if >=26.3 {
    /*public static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    *///?} else {
    public static final int MOUSE_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    public static final int MOUSE_RIGHT = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    //?}

    public static InputConstants.Type keyboard() {
        //? if >=26.3 {
        /*return InputConstants.Type.KEYBOARD;
        *///?} else {
        return InputConstants.Type.KEYSYM;
        //?}
    }

    public static boolean shiftHeld() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        *///?} else {
        return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        //?}
    }

    public static boolean rightMouseDown(Minecraft client) {
        if (client == null || client.getWindow() == null) return false;
        //? if >=26.3 {
        /*return (SDLMouse.nSDL_GetMouseState(0L, 0L) & SDLMouse.SDL_BUTTON_RMASK) != 0;
        *///?} else {
        return GLFW.glfwGetMouseButton(
                client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        //?}
    }
}
