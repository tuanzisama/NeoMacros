package ink.tuanzi.neomacros.port.model;

/**
 * Immutable snapshot of one item type's food component: how much hunger the
 * food restores, how much saturation it carries, and whether the food can
 * always be eaten.
 *
 * @param hunger       nutrition in hunger points (two points per bar segment)
 * @param saturation   saturation the food restores
 * @param alwaysEdible whether the food can be eaten at any hunger level
 */
public record FoodData(int hunger, float saturation, boolean alwaysEdible) {
}
