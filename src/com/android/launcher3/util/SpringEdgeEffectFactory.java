/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.launcher3.util;

import android.graphics.Canvas;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

public class SpringEdgeEffectFactory extends RecyclerView.EdgeEffectFactory {

    private static final float STIFFNESS = SpringForce.STIFFNESS_LOW;
    private static final float DAMPING_RATIO = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
    private static final float MAX_STRETCH_FRACTION = 0.15f;
    private static final float VELOCITY_SCALE = 0.6f;

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(@NonNull RecyclerView recyclerView, int direction) {
        return new SpringEdgeEffect(recyclerView, direction);
    }

    public static EdgeEffect createSpringEdgeEffect(RecyclerView recyclerView, int direction) {
        return new SpringEdgeEffect(recyclerView, direction);
    }

    private static class SpringEdgeEffect extends EdgeEffect {

        private final RecyclerView mRecyclerView;
        private final SpringAnimation mSpringAnimation;
        private final boolean mIsVertical;
        private final float mSign;
        private float mPullDistance = 0f;

        SpringEdgeEffect(RecyclerView recyclerView, int direction) {
            super(recyclerView.getContext());
            mRecyclerView = recyclerView;
            mIsVertical = direction == DIRECTION_TOP || direction == DIRECTION_BOTTOM;
            mSign = (direction == DIRECTION_TOP || direction == DIRECTION_LEFT) ? 1f : -1f;

            DynamicAnimation.ViewProperty property =
                    mIsVertical ? DynamicAnimation.TRANSLATION_Y : DynamicAnimation.TRANSLATION_X;

            mSpringAnimation = new SpringAnimation(recyclerView, property, 0f);
            mSpringAnimation.setSpring(new SpringForce(0f)
                    .setStiffness(STIFFNESS)
                    .setDampingRatio(DAMPING_RATIO));
        }

        @Override
        public void onPull(float deltaDistance) {
            onPull(deltaDistance, 0.5f);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            if (mSpringAnimation.isRunning()) {
                mSpringAnimation.cancel();
            }
            mPullDistance += deltaDistance;
            float size = mIsVertical ? mRecyclerView.getHeight() : mRecyclerView.getWidth();
            float maxStretch = size * MAX_STRETCH_FRACTION;
            float translation = Math.max(-maxStretch,
                    Math.min(maxStretch, mSign * mPullDistance * size));

            if (mIsVertical) {
                mRecyclerView.setTranslationY(translation);
            } else {
                mRecyclerView.setTranslationX(translation);
            }
        }

        @Override
        public void onRelease() {
            if (mPullDistance != 0f) {
                mPullDistance = 0f;
                mSpringAnimation.animateToFinalPosition(0f);
            }
        }

        @Override
        public void onAbsorb(int velocity) {
            mSpringAnimation.setStartVelocity(mSign * velocity * VELOCITY_SCALE);
            mSpringAnimation.animateToFinalPosition(0f);
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            return !mSpringAnimation.isRunning();
        }
    }
}
