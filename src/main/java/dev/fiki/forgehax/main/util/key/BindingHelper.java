package dev.fiki.forgehax.main.util.key;

import com.google.common.collect.Sets;
import dev.fiki.forgehax.main.util.reflection.FastReflection;
import lombok.Getter;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static dev.fiki.forgehax.main.Common.getGameSettings;
import static dev.fiki.forgehax.main.Common.requiresMainThreadExecution;

public class BindingHelper {

  private static final Set<BindHandle> KEYS_HANDLES = Sets.newHashSet();

  @Getter
  private static boolean suppressingSettingsPacket = false;

  static {
    // cause key input class to load
    InputMappings.Type.KEYSYM.getName();
    InputMappings.Type.MOUSE.getName();
  }

  private static final IKeyConflictContext EMPTY = new IKeyConflictContext() {
    @Override
    public boolean isActive() {
      return true;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
      return false;
    }
  };

  private static BindHandle getBindHandle(KeyBinding key) {
    for (BindHandle handle : KEYS_HANDLES) {
      if (handle.getKey() == key) {
        return handle;
      }
    }
    return null;
  }

  public static void restoreContextHandlers(Collection<KeyBinding> keys) {
    for (KeyBinding key : keys) {
      BindHandle handle = getBindHandle(key);
      if (handle != null) {
        handle.restoreContext();
        // not being used anymore
        if (handle.isRestored()) {
          KEYS_HANDLES.remove(handle);
        }
      }
    }
  }

  public static void restoreContextHandlers(KeyBinding... keys) {
    restoreContextHandlers(Arrays.asList(keys));
  }

  public static void restoreContextHandler(KeyBinding key) {
    restoreContextHandlers(Collections.singleton(key));
  }

  public static void disableContextHandlers(Collection<KeyBinding> keys) {
    for (KeyBinding key : keys) {
      BindHandle handle = getBindHandle(key);

      // create a new handle if one does not exist
      if (handle == null) {
        handle = new BindHandle(key);
        KEYS_HANDLES.add(handle);
      }

      // replace the current context handler
      handle.disableContext();
    }
  }

  public static void disableContextHandlers(KeyBinding... keys) {
    disableContextHandlers(Arrays.asList(keys));
  }

  public static void disableContextHandler(KeyBinding key) {
    disableContextHandlers(Collections.singleton(key));
  }

  private static String trimInputKeyName(InputMappings.Input input) {
    int len;
    if (!InputMappings.Type.MOUSE.equals(input.getType())) {
      len = (input.getType().getName() + ".").length();
    } else {
      len = "key.".length();
    }
    return input.getTranslationKey().substring(len);
  }

  public static InputMappings.Input getInputByName(String name) {
    return FastReflection.Fields.InputMappings_REGISTRY.get(null)
        .values()
        .stream()
        .filter(input -> input.getTranslationKey().equalsIgnoreCase(name)
            || trimInputKeyName(input).equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new Error("Unknown key: " + name));
  }

  public static InputMappings.Input getInputByKeyCode(int keyCode) {
    return FastReflection.Fields.InputMappings_REGISTRY.get(null)
        .values()
        .stream()
        .filter(input -> input.getKeyCode() == keyCode)
        .findFirst()
        .orElse(getInputUnknown());
  }

  public static InputMappings.Input getInputUnknown() {
    return InputMappings.INPUT_INVALID;
  }

  public static boolean isInputUnknown(InputMappings.Input input) {
    return getInputUnknown().equals(input);
  }

  public static IKeyConflictContext getEmptyKeyConflictContext() {
    return EMPTY;
  }

  public static void addBinding(KeyBinding binding) {
    requiresMainThreadExecution();

    ClientRegistry.registerKeyBinding(binding);
    updateKeyBindings();
  }

  public static boolean removeBinding(KeyBinding binding) {
    requiresMainThreadExecution();

    int i = ArrayUtils.indexOf(getGameSettings().keyBindings, binding);

    if (i != -1) {
      getGameSettings().keyBindings = ArrayUtils.remove(getGameSettings().keyBindings, i);
      updateKeyBindings();
      return true;
    }

    return false;
  }

  public static void updateKeyBindings() {
    requiresMainThreadExecution();
    KeyBinding.resetKeyBindingArrayAndHash();
  }

  public static void saveGameSettings() {
    if (getGameSettings() != null) {
      suppressingSettingsPacket = true;
      try {
        getGameSettings().saveOptions();
      } finally {
        suppressingSettingsPacket = false;
      }
    }
  }

  public static KeyBinding getKeyBindByDescription(String desc) {
    for (KeyBinding kb : getGameSettings().keyBindings) {
      if (kb.getKeyDescription().equalsIgnoreCase(desc.replace("-"," "))) {
        return kb;
      }
    }
    return null;
  }
}
