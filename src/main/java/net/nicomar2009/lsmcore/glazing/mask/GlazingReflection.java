package net.nicomar2009.lsmcore.glazing.mask;

import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;

/** The consumer can also swap its curtain LEFT/RIGHT when this returns true, keeping
 * vertical curtain parts unchanged. Accepts vanilla Mirror::mirror without any curtain enum.
 */
public final class GlazingReflection {
    private GlazingReflection() {}
    public static boolean reversesSides(Direction originalFacing,Direction reflectedFacing,UnaryOperator<Direction> reflection) {
        if (originalFacing==null || reflectedFacing==null || reflection==null
                || originalFacing.getAxis()==Direction.Axis.Y || reflectedFacing.getAxis()==Direction.Axis.Y)
            throw new IllegalArgumentException("Expected horizontal reflection");
        return reflection.apply(originalFacing.getClockWise())!=reflectedFacing.getClockWise();
    }
}
