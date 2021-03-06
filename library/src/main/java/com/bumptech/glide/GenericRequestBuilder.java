package com.bumptech.glide;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Animation;
import android.widget.ImageView;
import com.bumptech.glide.load.Encoder;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.SkipCache;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.UnitTransformation;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.NullEncoder;
import com.bumptech.glide.load.resource.bitmap.BitmapDecoder;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.manager.RequestTracker;
import com.bumptech.glide.provider.ChildLoadProvider;
import com.bumptech.glide.provider.LoadProvider;
import com.bumptech.glide.request.GenericRequest;
import com.bumptech.glide.request.GlideAnimationFactory;
import com.bumptech.glide.request.NoAnimation;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestCoordinator;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.ThumbnailRequestCoordinator;
import com.bumptech.glide.request.ViewAnimation;
import com.bumptech.glide.request.ViewPropertyAnimation;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.Target;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A generic class that can handle loading a bitmap either from an image or as a thumbnail from a video given
 * models loaders to translate a model into generic resources for either an image or a video and decoders that can
 * decode those resources into bitmaps.
 *
 * @param <ModelType> The type of model representing the image or video.
 * @param <DataType> The data type that the image {@link ModelLoader} will provide that can be decoded by the image
 *      {@link BitmapDecoder}.
 * @param <ResourceType> The type of the resource that will be loaded.
 */
public class GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> {
    private final Context context;
    private final ModelType model;
    private final ChildLoadProvider<ModelType, DataType, ResourceType, TranscodeType> loadProvider;
    private final Class<TranscodeType> transcodeClass;
    private final Glide glide;
    private final RequestTracker requestTracker;
    private List<Transformation<ResourceType>> transformations = null;
    private Transformation<ResourceType> singleTransformation = UnitTransformation.get();
    private int placeholderId;
    private int errorId;
    private RequestListener<ModelType, TranscodeType> requestListener;
    private Float thumbSizeMultiplier;
    private GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType>
            thumbnailRequestBuilder;
    private Float sizeMultiplier = 1f;
    private Drawable placeholderDrawable;
    private Drawable errorPlaceholder;
    private Priority priority = null;
    private boolean isCacheable = true;
    private ResourceEncoder<ResourceType> preSkipEncoder;
    private GlideAnimationFactory<TranscodeType> animationFactory = NoAnimation.getFactory();
    private int overrideHeight = -1;
    private int overrideWidth = -1;
    private boolean cacheSource = false;
    private Encoder<DataType> preSkipSourceEncoder;

    GenericRequestBuilder(Context context, ModelType model,
            LoadProvider<ModelType, DataType, ResourceType, TranscodeType> loadProvider,
            Class<TranscodeType> transcodeClass, Glide glide, RequestTracker requestTracker) {
        this.transcodeClass = transcodeClass;
        this.glide = glide;
        this.requestTracker = requestTracker;
        this.loadProvider = loadProvider != null ?
                new ChildLoadProvider<ModelType, DataType, ResourceType, TranscodeType>(loadProvider) : null;
        preSkipEncoder = loadProvider != null ? loadProvider.getEncoder() : null;

        if (context == null) {
            throw new NullPointerException("Context can't be null");
        }
        if (model != null && loadProvider == null) {
            throw new NullPointerException("LoadProvider must not be null");
        }
        this.context = context;
        this.model = model;
    }

    /**
     * Loads and displays the image retrieved by the given thumbnail request if it finishes before this request.
     * Best used for loading thumbnail images that are smaller and will be loaded more quickly than the fullsize
     * image. There are no guarantees about the order in which the requests will actually finish. However, if the
     * thumb request completes after the full request, the thumb image will never replace the full image.
     *
     * @see #thumbnail(float)
     *
     * <p>
     *     Note - Any options on the main request will not be passed on to the thumbnail request. For example, if
     *     you want an animation to occur when either the full image loads or the thumbnail loads, you need to call
     *     {@link #animate(int)} on both the thumb and the full request. For a simpler thumbnail option, see
     *     {@link #thumbnail(float)}.
     * </p>
     *
     * <p>
     *     Only the thumbnail call on the main request will be obeyed.
     * </p>
     *
     * @param thumbnailRequest The request to use to load the thumbnail.
     * @return This builder object.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> thumbnail(
            GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType>
                    thumbnailRequest) {
        this.thumbnailRequestBuilder = thumbnailRequest;

        return this;
    }

    /**
     * Loads an image in an identical manner to this request except with the dimensions of the target multiplied
     * by the given size multiplier. If the thumbnail load completes before the fullsize load, the thumbnail will
     * be shown. If the thumbnail load completes afer the fullsize load, the thumbnail will not be shown.
     *
     * <p>
     *     Note - The thumbnail image will be smaller than the size requested so the target (or {@link ImageView})
     *     must be able to scale the thumbnail appropriately. See {@link ImageView.ScaleType}.
     * </p>
     *
     * <p>
     *     Almost all options will be copied from the original load, including the {@link ModelLoader},
     *     {@link BitmapDecoder}, and {@link Transformation}s. However, {@link #placeholder(int)} and
     *     {@link #error(int)}, and {@link #listener(RequestListener)} will only be used on the fullsize load and
     *     will not be copied for the thumbnail load.
     * </p>
     *
     * <p>
     *     Only the thumbnail call on the main request will be obeyed.
     * </p>
     *
     * @param sizeMultiplier The multiplier to apply to the {@link Target}'s dimensions when loading the thumbnail.
     * @return This builder object.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> thumbnail(
            float sizeMultiplier) {
        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.thumbSizeMultiplier = sizeMultiplier;

        return this;
    }

    /**
     * Applies a multiplier to the {@link Target}'s size before loading the image. Useful for loading thumbnails
     * or trying to avoid loading huge bitmaps on devices with overly dense screens.
     *
     * @param sizeMultiplier The multiplier to apply to the {@link Target}'s dimensions when loading the image.
     * @return This builder object.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> sizeMultiplier(
            float sizeMultiplier) {
        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.sizeMultiplier = sizeMultiplier;

        return this;
    }

    /**
     * Loads the resource from the given data type using the given {@link BitmapDecoder}.
     *
     * <p>
     *     Will be ignored if the data represented by the given model is not a video.
     * </p>
     *
     * @param decoder The {@link BitmapDecoder} to use to decode the video resource.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> decoder(
            ResourceDecoder<DataType, ResourceType> decoder) {
        // loadProvider will be null if model is null, in which case we're not going to load anything so it's ok to
        // ignore the decoder.
        if (loadProvider != null) {
            loadProvider.setSourceDecoder(decoder);
        }

        return this;
    }

    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> cacheDecoder(
            ResourceDecoder <InputStream, ResourceType> cacheDecoder) {
        // loadProvider will be null if model is null, in which case we're not going to load anything so it's ok to
        // ignore the decoder.
        if (loadProvider != null) {
            loadProvider.setCacheDecoder(cacheDecoder);
        }

        return this;
    }

    /**
     * Sets the source encoder to use to encode the data retrieved by this request directly into cache. The returned
     * resouce will then be decoded from the cached data.
     *
     * <p>
     *     Note - This encoder will not be used unless
     * </p>
     *
     * @param sourceEncoder The encoder to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> sourceEncoder(
            Encoder<DataType> sourceEncoder) {
        if (loadProvider != null) {
            loadProvider.setSourceEncoder(sourceEncoder);
            preSkipSourceEncoder = sourceEncoder;
        }

        return this;
    }

    /**
     * Attempts to write the data retrieved by this request to cache first and then decodes the resource from the cached
     * source data. Only makes sense for remote or transient data as a means of either avoiding downloading the same
     * data repeatedly or preserving some content you expect to be removed.
     *
     * <p>
     *     Note that if this is set to true the {@link ResourceDecoder} set as the decoder will not be used, instead the
     *     cache decoder will be used.
     * </p>
     *
     * <p>
     *     If no {@link Encoder} is set or available for the given data type, this may cause the load to fail.
     * </p>
     *
     * @see #sourceEncoder(Encoder)
     * @see #decoder(ResourceDecoder)
     * @see #cacheDecoder(ResourceDecoder)
     * @see #skipCache(boolean)
     *
     * @param cacheSource True to write the data directly to cache .
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> cacheSource(boolean cacheSource) {
        this.cacheSource = cacheSource;
        if (!cacheSource) {
            if (loadProvider != null) {
                preSkipSourceEncoder = loadProvider.getSourceEncoder();
            }
            final Encoder<DataType> skipCache = NullEncoder.get();
            return sourceEncoder(skipCache);
        } else {
            return sourceEncoder(preSkipSourceEncoder);
        }
    }

    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> encoder(
            ResourceEncoder<ResourceType> encoder) {
        // loadProvider will be null if model is null, in which case we're not going to load anything so it's ok to
        // ignore the encoder.
        if (loadProvider != null) {
            loadProvider.setEncoder(encoder);
            preSkipEncoder = encoder;
        }

        return this;
    }

    /**
     * Sets the priority for this load.
     *
     * @param priority A priority.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> priority(
            Priority priority) {
        this.priority = priority;

        return this;
    }

    /**
     * Transform images with the given {@link Transformation}. Appends this transformation onto any existing
     * transformations
     *
     * @param transformation the transformation to apply.
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> transform(
            Transformation<ResourceType> transformation) {
        if (singleTransformation == UnitTransformation.get()) {
            singleTransformation = transformation;
        } else {
            transformations = new ArrayList<Transformation<ResourceType>>();
            transformations.add(singleTransformation);
            transformations.add(transformation);
        }

        return this;
    }

    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> transcoder(
            ResourceTranscoder<ResourceType, TranscodeType> transcoder) {
        if (loadProvider != null) {
            loadProvider.setTranscoder(transcoder);
        }

        return this;
    }

    /**
     * Sets an animation to run on the wrapped target when an image load finishes. Will only be run if the image
     * was loaded asynchronously (ie was not in the memory cache)
     *
     * @param animationId The resource id of the animation to run
     * @return This RequestBuilder
     */
    // This is safe because the view animation doesn't care about the resource type it receives.
    @SuppressWarnings("unchecked")
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(
            int animationId) {
        return animate(new ViewAnimation.ViewAnimationFactory(context, animationId));
    }

    /**
     * Sets an animation to run on the wrapped target when an image load finishes. Will only be run if the image
     * was loaded asynchronously (ie was not in the memory cache)
     *
     * @param animation The animation to run
     * @return This RequestBuilder
     */
    // This is safe because the view animation doesn't care about the resource type it receives.
    @SuppressWarnings("unchecked")
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(
            Animation animation) {
        return animate(new ViewAnimation.ViewAnimationFactory(animation));
    }

    /**
     * Sets an animator to run a {@link ViewPropertyAnimator} on a view that the target may be wrapping when a resource
     * load finishes. Will only be run if the load was loaded asynchronously (ie was not in the memory cache).
     *
     * @param animator The {@link ViewPropertyAnimation.Animator} to run.
     * @return This RequestBuilder.
     */
    // This is safe because the view property animation doesn't care about the resource type it receives.
    @SuppressWarnings("unchecked")
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(
            ViewPropertyAnimation.Animator animator) {
        return animate(new ViewPropertyAnimation.ViewPropertyAnimationFactory(animator));
    }

    GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(
            GlideAnimationFactory<TranscodeType> animationFactory) {
        if (animationFactory == null) {
            throw new NullPointerException("Animation factory must not be null!");
        }
        this.animationFactory = animationFactory;

        return this;
    }

    /**
     * Sets a resource to display while an image is loading
     *
     * @param resourceId The id of the resource to use as a placeholder
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> placeholder(
            int resourceId) {
        this.placeholderId = resourceId;

        return this;
    }

    /**
     * Sets a drawable to display while an image is loading.
     *
     * @param drawable The drawable to display as a placeholder.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> placeholder(
            Drawable drawable) {
        this.placeholderDrawable = drawable;

        return this;
    }

    /**
     * Sets a resource to display if a load fails
     *
     * @param resourceId The id of the resource to use as a placeholder
     * @return This request
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> error(
            int resourceId) {
        this.errorId = resourceId;

        return this;
    }

    /**
     * Sets a {@link Drawable} to display if a load fails.
     *
     * @param drawable The drawable to display.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> error(
            Drawable drawable) {
        this.errorPlaceholder = drawable;

        return this;
    }

    /**
     * Sets a RequestBuilder listener to monitor the image load. It's best to create a single instance of an
     * exception handler per type of request (usually activity/fragment) rather than pass one in per request to
     * avoid some redundant object allocation.
     *
     * @param requestListener The request listener to use.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> listener(
            RequestListener<ModelType, TranscodeType> requestListener) {
        this.requestListener = requestListener;

        return this;
    }

    /**
     * Allows the loaded resource to skip the memory cache.
     *
     * <p>
     *     Note - this is not a guarantee. If a request is already pending for this resource and that request is not
     *     also skipping the memory cache, the resource will be cached in memory.
     * </p>
     *
     * @param skip True to allow the resource to skip the memory cache.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> skipMemoryCache(boolean skip) {
        this.isCacheable = !skip;

        return this;
    }

    /**
     * Allows the loaded resource to skip the disk cache.
     *
     * <p>
     *     Note - this is not a guarantee. If a request is already pending for this resource and that request is not
     *     also skipping the disk cache, the resource will be cached on disk.
     * </p>
     *
     * @param skip True to allow the resource to skip the disk cache.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> skipDiskCache(boolean skip) {
        if (skip) {
            if (loadProvider != null) {
                preSkipEncoder = loadProvider.getEncoder();
            }
            final SkipCache<ResourceType> skipCache = SkipCache.get();
            return encoder(skipCache);
        } else {
            return encoder(preSkipEncoder);
        }
    }

    /**
     * Allows the resource to skip both the memory and the disk cache.
     *
     * @see #skipDiskCache(boolean)
     * @see #skipMemoryCache(boolean)
     *
     * @param skip True to allow the resource to skip both the memory and the disk cache.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> skipCache(boolean skip) {
        skipMemoryCache(skip);
        skipDiskCache(skip);
        cacheSource(false);

        return this;
    }

    /**
     * Overrides the {@link Target}'s width and height with the given values. This is useful almost exclusively for
     * thumbnails, and should only be used when you both need a very specific sized image and when it is impossible or
     * impractical to return that size from {@link Target#getSize(Target.SizeReadyCallback)}.
     *
     * @param width The width to use to load the resource.
     * @param height The height to use to load the resource.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> override(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("Width must be >= 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be >= 0");
        }
        this.overrideWidth = width;
        this.overrideHeight = height;

        return this;
    }

    /**
     * Set the target the image will be loaded into.
     *
     * @param target The target to load te image for
     * @return The given target.
     */
    public <Y extends Target<TranscodeType>> Y into(Y target) {
        Request previous = target.getRequest();

        if (previous != null) {
            previous.clear();
            requestTracker.removeRequest(previous);
            previous.recycle();
        }

        Request request = buildRequest(target);
        target.setRequest(request);
        requestTracker.addRequest(request);
        request.run();

        return target;
    }

    /**
     * Sets the {@link ImageView} the image will be loaded into, cancels any existing loads into the view, and frees
     * any resources Glide has loaded into the view so they may be reused.
     *
     * @see Glide#clear(View)
     *
     * @param view The view to cancel previous loads for and load the new image into.
     * @return The {@link BitmapImageViewTarget} used to wrap the given {@link ImageView}.
     */
    public Target<TranscodeType> into(ImageView view) {
        return into(glide.buildImageViewTarget(view, transcodeClass));
    }

    private Priority getThumbnailPriority() {
        final Priority result;
        if (priority == Priority.LOW) {
            result = Priority.NORMAL;
        } else if (priority == Priority.NORMAL) {
            result = Priority.HIGH;
        } else {
            result = Priority.IMMEDIATE;
        }
        return result;
    }

    private Request buildRequest(Target<TranscodeType> target) {
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        return buildRequestRecursive(target, null);
    }

    private Request buildRequestRecursive(Target<TranscodeType> target, ThumbnailRequestCoordinator parentCoordinator) {
        if (thumbnailRequestBuilder != null) {
            // Recursive case: contains a potentially recursive thumbnail request builder.
            if (thumbnailRequestBuilder.animationFactory.equals(NoAnimation.getFactory())) {
                thumbnailRequestBuilder.animationFactory = animationFactory;
            }

            if (thumbnailRequestBuilder.requestListener == null && requestListener != null) {
                thumbnailRequestBuilder.requestListener = requestListener;
            }

            if (thumbnailRequestBuilder.priority == null) {
                thumbnailRequestBuilder.priority = getThumbnailPriority();
            }

            ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
            Request fullRequest = obtainRequest(target, sizeMultiplier, priority, coordinator);
            // Recursively generate thumbnail requests.
            Request thumbRequest = thumbnailRequestBuilder.buildRequestRecursive(target, coordinator);
            coordinator.setRequests(fullRequest, thumbRequest);
            return coordinator;
        } else if (thumbSizeMultiplier != null) {
            // Base case: thumbnail multiplier generates a thumbnail request, but cannot recurse.
            ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
            Request fullRequest = obtainRequest(target, sizeMultiplier, priority, coordinator);
            Request thumbnailRequest = obtainRequest(target, thumbSizeMultiplier, getThumbnailPriority(), coordinator);
            coordinator.setRequests(fullRequest, thumbnailRequest);
            return coordinator;
        } else {
            // Base case: no thumbnail.
            return obtainRequest(target, sizeMultiplier, priority, parentCoordinator);
        }
    }

    private <Z> Request obtainRequest(Target<TranscodeType> target, float sizeMultiplier, Priority priority,
            RequestCoordinator requestCoordinator) {
        if (model == null) {
            requestCoordinator = null;
        }

        return GenericRequest.obtain(
                loadProvider,
                model,
                context,
                priority,
                target,
                sizeMultiplier,
                placeholderDrawable,
                placeholderId,
                errorPlaceholder,
                errorId,
                requestListener,
                requestCoordinator,
                glide.getEngine(),
                getFinalTransformation(),
                transcodeClass,
                isCacheable,
                animationFactory,
                overrideWidth,
                overrideHeight,
                cacheSource);
    }

    @SuppressWarnings("unchecked")
    private Transformation<ResourceType> getFinalTransformation() {
        if (transformations == null) {
            return singleTransformation;
        } else {
            return new MultiTransformation<ResourceType>(transformations);
        }
    }
}
