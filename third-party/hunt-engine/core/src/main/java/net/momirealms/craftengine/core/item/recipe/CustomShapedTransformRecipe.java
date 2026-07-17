package net.momirealms.craftengine.core.item.recipe;

import com.google.gson.JsonObject;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.recipe.input.CraftingInput;
import net.momirealms.craftengine.core.item.recipe.input.RecipeInput;
import net.momirealms.craftengine.core.item.recipe.result.CustomRecipeResult;
import net.momirealms.craftengine.core.item.recipe.transform.ItemTransformDataProcessor;
import net.momirealms.craftengine.core.item.recipe.transform.ItemTransformDataProcessors;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.plugin.config.KnownResourceException;
import net.momirealms.craftengine.core.plugin.context.CommonConditions;
import net.momirealms.craftengine.core.plugin.context.CommonFunctions;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.Function;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class CustomShapedTransformRecipe extends CustomShapedRecipe {
    public static final Serializer SERIALIZER = new Serializer();

    private final boolean mergeComponents;
    private final List<ItemTransformDataProcessor> processors;

    public CustomShapedTransformRecipe(Key id,
                                       boolean showNotification,
                                       CustomRecipeResult result,
                                       CustomRecipeResult visualResult,
                                       String group,
                                       CraftingRecipeCategory category,
                                       CustomShapedRecipe.Pattern pattern,
                                       Function<Context>[] craftingFunctions,
                                       Predicate<Context> craftingCondition,
                                       boolean ingredientCountSupport,
                                       List<ItemTransformDataProcessor> processors,
                                       boolean mergeComponents
    ) {
        super(id, showNotification, result, visualResult, group, category, pattern, craftingFunctions, craftingCondition, true, ingredientCountSupport);
        this.processors = processors;
        this.mergeComponents = mergeComponents;
    }

    public boolean mergeComponents() {
        return this.mergeComponents;
    }

    @Override
    public boolean requiresInput() {
        return true;
    }

    @Override
    public @NotNull Key serializerType() {
        return RecipeSerializers.SHAPED_TRANSFORM;
    }

    @Override
    public Item assembleVisual(RecipeInput input, ItemBuildContext context) {
        Item source;
        if (!super.parsedPattern.symmetrical) {
            source = ((ParsedPattern) super.parsedPattern).matchSource((CraftingInput) input, true);
            if (source == null) {
                source = ((ParsedPattern) super.parsedPattern).matchSource((CraftingInput) input, false);
            }
        } else {
            source = ((ParsedPattern) super.parsedPattern).matchSource((CraftingInput) input, false);
        }
        if (source == null) {
            return super.assembleVisual(input, context);
        }
        Item result = this.visualResult().buildItem(context);
        return createTransformResult(source, result);
    }

    @Override
    public Item assemble(RecipeInput input, ItemBuildContext context) {
        Item source;
        if (!super.parsedPattern.symmetrical) {
            source = ((ParsedPattern) super.parsedPattern).matchSource((CraftingInput) input, true);
            if (source == null) {
                source = ((ParsedPattern) super.parsedPattern).matchSource((CraftingInput) input, false);
            }
        } else {
            source = ((ParsedPattern) super.parsedPattern).matchSource((CraftingInput) input, false);
        }
        if (source == null) {
            return super.assemble(input, context);
        }
        Item result = this.result(context);
        return createTransformResult(source, result);
    }

    private Item createTransformResult(Item base, Item result) {
        Item finalResult;
        if (this.mergeComponents) {
            finalResult = base.mergeCopy(result);
        } else {
            finalResult = result.copy();
        }
        if (this.processors != null) {
            for (ItemTransformDataProcessor processor : this.processors) {
                processor.accept(base, result, finalResult);
            }
        }
        return finalResult;
    }

    public static class Pattern extends CustomShapedRecipe.Pattern {
        protected final Key id;
        protected final char sourceChar;

        public Pattern(Key id, String[] pattern, Map<Character, Ingredient> ingredients, char sourceChar) {
            super(pattern, ingredients);
            this.id = id;
            this.sourceChar = sourceChar;
        }

        @Override
        public ParsedPattern parse() {
            String[] shrunk = shrink(this.pattern);
            for (int i = 0; i < pattern.length; i++) {
                char[] line = pattern[i].toCharArray();
                for (int j = 0; j < line.length; j++) {
                    if (line[j] == sourceChar) {
                        int sourceIndex = i * line.length + j;
                        int mirroredJ = line.length - 1 - j;
                        int mirroredSourceIndex = i * line.length + mirroredJ;
                        return new ParsedPattern(
                                shrunk[0].length(),
                                shrunk.length,
                                toIngredientArray(shrunk, this.ingredients),
                                sourceIndex,
                                mirroredSourceIndex
                        );
                    }
                }
            }
            throw new RuntimeException("Invalid pattern for recipe: " + id.asString());
        }
    }

    public static class ParsedPattern extends CustomShapedRecipe.ParsedPattern {
        protected final int sourceIndex;
        protected final int mirroredSourceIndex;

        public ParsedPattern(int width, int height, Optional<Ingredient>[] ingredients, int sourceIndex, int mirroredSourceIndex) {
            super(width, height, ingredients);
            this.sourceIndex = sourceIndex;
            this.mirroredSourceIndex = this.symmetrical ? -1 : mirroredSourceIndex;
        }

        Item matchSource(CraftingInput input, boolean mirrored) {
            Optional<Ingredient>[] ingredients = mirrored ? this.mirroredIngredients : this.ingredients;
            int sourceIndex = mirrored ? this.mirroredSourceIndex : this.sourceIndex;
            for (int i = 0, size = input.size(); i < size; i++) {
                Optional<Ingredient> optional = ingredients[i];
                UniqueIdItem itemStack = input.getItem(i);
                if (!Ingredient.isInstance(optional, itemStack)) {
                    return null;
                }
            }
            return sourceIndex > -1 && sourceIndex < input.size() ? input.getItem(sourceIndex).item() : null;
        }
    }

    public static class Serializer extends AbstractRecipeSerializer<CustomShapedTransformRecipe> {
        private static final String[] MERGE_COMPONENTS = new String[]{"merge-components", "merge_components"};

        @SuppressWarnings({"unchecked", "DuplicatedCode"})
        @Override
        public CustomShapedTransformRecipe readConfig(Key id, ConfigSection section) {
            ConfigSection ingredientSection = section.getNonNullSection(INGREDIENTS);
            Map<Character, Ingredient> ingredients = new HashMap<>();
            boolean hasAdditionalIngredients = false;

            char sourceIngredientChar = ' ';
            Ingredient sourceIngredient = null;
            for (String ingredientChar : ingredientSection.keySet()) {
                if (ingredientChar.length() != 1 || ingredientChar.equals(" ")) {
                    throw new KnownResourceException("resource.recipe.shaped.invalid_symbol", ingredientSection.path(), ingredientChar);
                }
                char ch = ingredientChar.charAt(0);
                Ingredient ingredient = ingredientSection.getNonNullValue(ingredientChar, ConfigConstants.ARGUMENT_LIST, super::parseIngredient);
                ConfigValue value = ingredientSection.getValue(ingredientChar);
                if (value != null && value.is(Map.class) && value.getAsSection().getBoolean("source", false)) {
                    if (sourceIngredient == null) {
                        sourceIngredientChar = ch;
                        sourceIngredient = ingredient;
                    } else {
                        throw new KnownResourceException("resource.recipe.shaped_transform.ambigious_source", ingredientSection.path(), String.valueOf(ch),  String.valueOf(sourceIngredientChar));
                    }
                }

                ingredients.put(ch, ingredient);
                if (ingredient.count() > 1) {
                    hasAdditionalIngredients = true;
                }
            }
            if (sourceIngredient == null || sourceIngredientChar == ' ') {
                throw new KnownResourceException("resource.recipe.shaped_transform.source_not_found", ingredientSection.path());
            }
            List<String> pattern = section.getNonEmptyList("pattern", ConfigValue::getAsString);
            if (!validatePattern(pattern, sourceIngredientChar)) {
                throw new KnownResourceException("resource.recipe.shaped.invalid_pattern", section.assemblePath("pattern"), pattern.toString());
            }
            return new CustomShapedTransformRecipe(
                    id,
                    section.getBoolean(SHOW_NOTIFICATIONS, true),
                    super.parseResult(section.getNonNullValue("result", ConfigConstants.ARGUMENT_SECTION)),
                    section.getValue(VISUAL_RESULT, super::parseResult),
                    section.getString("group"),
                    section.getEnum("category", CraftingRecipeCategory.class),
                    new Pattern(id, pattern.toArray(new String[0]), ingredients, sourceIngredientChar),
                    section.getList(FUNCTIONS, CommonFunctions::fromConfig).toArray(new Function[0]),
                    MiscUtils.allOf(section.getList(CONDITIONS, CommonConditions::fromConfig)),
                    hasAdditionalIngredients,
                    section.getList(TRANSFORM_PROCESSOR, ItemTransformDataProcessors::fromConfig),
                    section.getBoolean(MERGE_COMPONENTS, true)
            );
        }

        @Override
        public CustomShapedTransformRecipe readJson(Key id, JsonObject json) {
            throw new IllegalArgumentException("unsupported recipe type for datapack: shaped_transform");
        }

        private boolean validatePattern(List<String> pattern, char sourceIngredient) {
            String first = pattern.getFirst();
            int length = first.length();
            boolean appear = false;
            for (String s : pattern) {
                if (s.length() != length) {
                    return false;
                }
                if (s.length() > 3) {
                    return false;
                }
                for (char c : s.toCharArray()) {
                    if (c == sourceIngredient) {
                        if (!appear) {
                            appear = true;
                        } else {
                            return false;
                        }
                    }
                }
            }
            return pattern.size() <= 3;
        }
    }
}
