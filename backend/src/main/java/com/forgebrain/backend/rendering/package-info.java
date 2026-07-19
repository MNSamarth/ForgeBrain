/**
 * Rendering-domain supporting types that go beyond the generic {@link
 * com.forgebrain.backend.models.RenderJob}/{@link com.forgebrain.backend.models.VideoPackage}
 * contracts. Two layers live here:
 *
 * <ul>
 *   <li>The rendering <b>foundation</b> — {@link com.forgebrain.backend.rendering.RenderPlan}
 *       and everything that builds ({@link com.forgebrain.backend.rendering.RenderPlanBuilder}),
 *       enriches ({@link com.forgebrain.backend.rendering.AssetCollector}), and checks ({@link
 *       com.forgebrain.backend.rendering.RenderValidator}) it. Pure transformation from a {@link
 *       com.forgebrain.backend.models.Storyboard} — no rendering/encoding logic.</li>
 *   <li>The still-unimplemented <b>execution seam</b> — {@link
 *       com.forgebrain.backend.rendering.SceneRenderInstruction} and {@link
 *       com.forgebrain.backend.rendering.RenderEngine} — where a real rendering technology
 *       would eventually plug in, once Voice Generation and Asset Management exist to resolve
 *       a {@code RenderPlan}'s abstract references into real files.</li>
 * </ul>
 */
package com.forgebrain.backend.rendering;
