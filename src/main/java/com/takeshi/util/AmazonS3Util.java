package com.takeshi.util;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.net.URLEncodeUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.takeshi.component.RedisComponent;
import com.takeshi.config.StaticConfig;
import com.takeshi.config.properties.AWSSecretsManagerCredentials;
import com.takeshi.constants.TakeshiCode;
import com.takeshi.constants.TakeshiDatePattern;
import com.takeshi.enums.TakeshiRedisKeyEnum;
import com.takeshi.exception.TakeshiException;
import com.takeshi.pojo.vo.AmazonS3VO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.redisson.api.RLock;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AmazonS3Util
 * <pre>{@code
 * implementation 'com.amazonaws:aws-java-sdk-s3:+'
 * implementation 'com.amazonaws:aws-java-sdk-secretsmanager:+'
 * }</pre>
 *
 * @author 七濑武【Nanase Takeshi】
 */
@Slf4j
public final class AmazonS3Util {

    // 文件原始名称
    private static final String ORIGINAL_NAME = "Original-Name";
    // 文件扩展名，例如：（.png）
    private static final String EXTENSION_NAME = "Extension-Name";
    // 保存到S3的时间
    private static final String CREATE_TIME = "Create-Time";
    // 视频时长，单位（微秒）
    private static final String LENGTH_IN_TIME = "Length-In-Time";
    // 视频封面缩略图URL
    private static final String COVER_THUMBNAIL = "Cover-Thumbnail";

    /*
     * X-NT都是临时签名URL中的参数名
     */

    // 原始文件名
    private static final String S3_ORIGINAL_FULL_NAME = "X-NT-OriginalFullName";
    // 文件大小，单位（字节）
    private static final String S3_CONTENT_LENGTH = "X-NT-ContentLength";
    // 内容类型
    private static final String S3_CONTENT_TYPE = "X-NT-ContentType";
    // 视频时长，单位（微秒）
    private static final String S3_LENGTH_IN_TIME = "X-NT-LengthInTime";
    // 视频封面缩略图URL
    private static final String S3_THUMBNAIL = "X-NT-Thumbnail";

    // 存储桶名称
    private static String BUCKET_NAME;
    // 预签名URL的过期时间
    private static Duration EXPIRATION_TIME;

    /**
     * 获取到的密钥信息
     */
    private static volatile JsonNode JSON_NODE;

    /**
     * 用于管理到 Amazon S3 的传输的高级实用程序
     */
    public static volatile TransferManager transferManager;

    static {
        if (ObjUtil.isNull(transferManager)) {
            synchronized (AmazonS3Util.class) {
                if (ObjUtil.isNull(transferManager)) {
                    try {
                        // 获取密钥
                        AWSSecretsManagerCredentials awsSecrets = StaticConfig.takeshiProperties.getAwsSecrets();
                        BUCKET_NAME = awsSecrets.getBucketName();
                        EXPIRATION_TIME = awsSecrets.getExpirationTime();
                        AWSSecretsManager awsSecretsManager = AWSSecretsManagerClientBuilder.standard()
                                .withRegion(awsSecrets.getRegion())
                                .withCredentials(new AWSStaticCredentialsProvider(awsSecrets))
                                .build();
                        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest();
                        getSecretValueRequest.setSecretId(awsSecrets.getSecretId());
                        GetSecretValueResult getSecretValueResult = awsSecretsManager.getSecretValue(getSecretValueRequest);
                        String secret = StrUtil.isNotBlank(getSecretValueResult.getSecretString()) ? getSecretValueResult.getSecretString() : new String(java.util.Base64.getDecoder().decode(getSecretValueResult.getSecretBinary()).array());
                        JSON_NODE = StaticConfig.objectMapper.readValue(secret, JsonNode.class);
                        String accessKey = JSON_NODE.get(awsSecrets.getAccessKeySecrets()).asText();
                        String secretKey = JSON_NODE.get(awsSecrets.getSecretKeySecrets()).asText();
                        // S3
                        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
                                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                                .withRegion(awsSecrets.getRegion())
                                .build();
                        if (!amazonS3.doesBucketExistV2(BUCKET_NAME)) {
                            // 创建桶
                            amazonS3.createBucket(BUCKET_NAME);
                            // 设置生命周期规则，指示自生命周期启动后必须经过7天才能中止并删除不完整的分段上传
                            BucketLifecycleConfiguration.Rule lifecycleRule = new BucketLifecycleConfiguration.Rule()
                                    .withId("Automatically delete incomplete multipart upload after seven days")
                                    .withAbortIncompleteMultipartUpload(new AbortIncompleteMultipartUpload().withDaysAfterInitiation(7))
                                    .withStatus(BucketLifecycleConfiguration.ENABLED);
                            // 将生命周期规则设置到桶中
                            amazonS3.setBucketLifecycleConfiguration(BUCKET_NAME, new BucketLifecycleConfiguration().withRules(lifecycleRule));
                            // 设置跨域规则
                            CORSRule corsRule = new CORSRule().withAllowedMethods(Collections.singletonList(CORSRule.AllowedMethods.GET)).withAllowedOrigins(Collections.singletonList("*"));
                            // 将跨域规则设置到桶中
                            amazonS3.setBucketCrossOriginConfiguration(BUCKET_NAME, new BucketCrossOriginConfiguration().withRules(corsRule));
                            // 为指定的存储桶启用传输加速
                            amazonS3.setBucketAccelerateConfiguration(new SetBucketAccelerateConfigurationRequest(BUCKET_NAME, new BucketAccelerateConfiguration(BucketAccelerateStatus.Enabled)));
                        }
                        transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();
                        log.info("AmazonS3Util.static --> TransferManager Initialization successful");
                    } catch (Exception e) {
                        log.error("AmazonS3Util.static --> TransferManager initialization failed, e: ", e);
                    }
                }
            }
        }
    }

    private AmazonS3Util() {
    }

    /**
     * 获取密钥信息的JsonNode
     *
     * @return JsonNode
     */
    public static JsonNode getSecret() {
        return JSON_NODE;
    }

    /**
     * 根据指定转化的类，获取密钥信息
     *
     * @param beanClass beanClass
     * @param <T>       T
     * @return T
     */
    public static <T> T getSecret(Class<T> beanClass) {
        return StaticConfig.objectMapper.convertValue(JSON_NODE, beanClass);
    }

    /**
     * 删除桶
     *
     * @param bucketName 桶名称
     */
    public static void deleteBucket(String bucketName) {
        transferManager.getAmazonS3Client().deleteBucket(bucketName);
    }

    /**
     * 上传经过压缩的图片
     *
     * @param file    要上传的图片文件
     * @param quality 压缩比例，必须为0~1
     * @return S3文件访问URL
     */
    public static AmazonS3VO uploadCompressImg(File file, float quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Img.from(file).setQuality(quality).write(byteArrayOutputStream);
        return uploadData(FileUtil.readBytes(file), file.getName());
    }

    /**
     * 上传文件，自动根据不同文件类型创建不同目录存放文件
     *
     * @param file 要上传的文件
     * @return S3文件访问URL
     */
    public static AmazonS3VO uploadFile(File file) {
        return uploadData(FileUtil.readBytes(file), file.getName());
    }

    /**
     * 上传文件，自动根据不同文件类型创建不同目录存放文件
     *
     * @param multipartFile 要上传的文件
     * @return S3文件访问URL
     */
    @SneakyThrows
    public static AmazonS3VO uploadFile(MultipartFile multipartFile) {
        return uploadData(multipartFile.getBytes(), multipartFile.getOriginalFilename());
    }

    /**
     * 上传多个文件
     *
     * @param multipartFiles 要上传的多文件数组
     * @return 多个S3文件访问URL
     */
    public static List<AmazonS3VO> uploadFile(MultipartFile[] multipartFiles) {
        ArrayList<AmazonS3VO> list = new ArrayList<>();
        for (MultipartFile item : multipartFiles) {
            list.add(uploadFile(item));
        }
        return list;
    }

    /**
     * 上传多个文件
     *
     * @param files 要上传的多文件数组
     * @return 多个S3文件访问URL
     */
    public static List<AmazonS3VO> uploadFile(File[] files) {
        ArrayList<AmazonS3VO> list = new ArrayList<>();
        for (File file : files) {
            list.add(uploadFile(file));
        }
        return list;
    }

    /**
     * 根据S3文件的key删除文件
     *
     * @param key S3对象的键
     */
    public static void deleteFile(String key) {
        transferManager.getAmazonS3Client().deleteObject(BUCKET_NAME, key);
    }

    /**
     * 根据S3文件的key下载到指定目录文件
     *
     * @param key     S3对象的键
     * @param outFile 存储的目录文件
     */
    @SneakyThrows
    public static void download(String key, File outFile) {
        transferManager.download(BUCKET_NAME, key, outFile).waitForCompletion();
    }

    /**
     * 上传文件，自动根据不同文件类型创建不同目录存放文件
     *
     * @param data     要上传的文件字节数组
     * @param fileName 完整的文件名
     * @return S3文件Key
     */
    @SneakyThrows
    public static AmazonS3VO uploadData(byte[] data, String fileName) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(data)) {
            String mediaType = TakeshiUtil.getTika().detect(tikaInputStream, fileName);
            MimeType mimeType = MimeTypes.getDefaultMimeTypes().forName(mediaType);
            String extension = mimeType.getExtension();
            if (StrUtil.isBlank(extension)) {
                throw new TakeshiException(TakeshiCode.FILE_TYPE_ERROR);
            }
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(tikaInputStream.getLength());
            metadata.setContentType(mediaType);
            // 添加用户自定义元数据
            String mainName = URLEncodeUtil.encode(FileNameUtil.mainName(fileName));
            metadata.addUserMetadata(ORIGINAL_NAME, mainName);
            metadata.addUserMetadata(CREATE_TIME, String.valueOf(Instant.now().toEpochMilli()));
            metadata.addUserMetadata(EXTENSION_NAME, extension);

            Upload thumbnailUpload = null;
            if (mediaType.startsWith("video/") || "image/gif".equals(mediaType)) {
                // 是视频或GIF
                try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                    FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber(tikaInputStream);
                    frameGrabber.start();
                    if (mediaType.startsWith("video/")) {
                        // 获取视频时长
                        metadata.addUserMetadata(LENGTH_IN_TIME, String.valueOf(frameGrabber.getLengthInTime()));
                    }
                    Frame frame = frameGrabber.grabImage();
                    // 提取第一帧作为封面
                    BufferedImage bufferedImage = converter.getBufferedImage(frame);
                    frameGrabber.stop();
                    try (TikaInputStream thumbnailTikaInputStream = TikaInputStream.get(ImgUtil.toBytes(bufferedImage, ImgUtil.IMAGE_TYPE_JPG))) {
                        ObjectMetadata thumbnailMetadata = new ObjectMetadata();
                        thumbnailMetadata.setContentLength(thumbnailTikaInputStream.getLength());
                        thumbnailMetadata.setContentType(MediaType.image(ImgUtil.IMAGE_TYPE_JPG).toString());
                        // 添加用户自定义元数据
                        thumbnailMetadata.addUserMetadata(ORIGINAL_NAME, mainName);
                        thumbnailMetadata.addUserMetadata(CREATE_TIME, String.valueOf(Instant.now().toEpochMilli()));
                        thumbnailMetadata.addUserMetadata(EXTENSION_NAME, StrUtil.DOT + ImgUtil.IMAGE_TYPE_JPG);
                        String thumbnailObjKey = getThumbnailObjKey();
                        // 添加视频/GIF封面图缩略图的S3 key
                        metadata.addUserMetadata(COVER_THUMBNAIL, thumbnailObjKey);
                        PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, thumbnailObjKey, thumbnailTikaInputStream, thumbnailMetadata);
                        thumbnailUpload = transferManager.upload(putObjectRequest);
                    }
                }
            }

            String fileObjKey = getFileObjKey(extension);
            PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, fileObjKey, tikaInputStream, metadata);
            // TransferManager 异步处理所有传输,所以这个调用立即返回
            Upload upload = transferManager.upload(putObjectRequest);
            // 等待此传输完成，这是一个阻塞调用；当前线程被挂起，直到这个传输完成
            if (ObjUtil.isNotNull(thumbnailUpload)) {
                thumbnailUpload.waitForUploadResult();
            }
            upload.waitForCompletion();
            return new AmazonS3VO(fileObjKey, getPresignedUrl(fileObjKey));
        }
    }

    /**
     * 返回用于访问 Amazon S3 资源的预签名 URL
     *
     * @param key S3对象的键
     * @return URL
     */
    public static URL getPresignedUrl(String key) {
        return getPresignedUrl(key, EXPIRATION_TIME);
    }

    /**
     * 返回用于访问 Amazon S3 资源的预签名 URL
     *
     * @param key            S3对象的键
     * @param expirationTime 预签名 URL 将过期的时间（单位：秒）
     * @return URL
     */
    public static URL getPresignedUrl(String key, Long expirationTime) {
        return getPresignedUrl(key, Duration.ofSeconds(expirationTime));
    }

    /**
     * 返回用于访问 Amazon S3 资源的预签名 URL
     *
     * @param fileKey  S3对象的键
     * @param duration 预签名 URL 将过期的时间
     * @return URL
     */
    public static URL getPresignedUrl(String fileKey, Duration duration) {
        if (StrUtil.isBlank(fileKey)) {
            return null;
        }
        RedisComponent redisComponent = StaticConfig.redisComponent;
        String redisKey = TakeshiRedisKeyEnum.S3_PRESIGNED_URL.projectKey(fileKey, duration);
        URL presignedUrl = toUrl(redisComponent.get(redisKey));
        if (ObjUtil.isNull(presignedUrl)) {
            RLock lock = redisComponent.getLock(TakeshiRedisKeyEnum.LOCK_S3_PRESIGNED_URL.projectKey(fileKey, duration));
            try {
                if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                    presignedUrl = toUrl(redisComponent.get(redisKey));
                    if (ObjUtil.isNull(presignedUrl)) {
                        ObjectMetadata objectMetadata = getObjectMetadata(fileKey);
                        if (ObjUtil.isNull(objectMetadata)) {
                            return null;
                        }
                        Date date = Date.from(Instant.now().plus(duration));
                        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(BUCKET_NAME, fileKey)
                                .withExpiration(date);
                        generatePresignedUrlRequest.addRequestParameter(S3_ORIGINAL_FULL_NAME, objectMetadata.getUserMetaDataOf(ORIGINAL_NAME) + objectMetadata.getUserMetaDataOf(EXTENSION_NAME));
                        generatePresignedUrlRequest.addRequestParameter(S3_CONTENT_LENGTH, String.valueOf(objectMetadata.getContentLength()));
                        generatePresignedUrlRequest.addRequestParameter(S3_CONTENT_TYPE, objectMetadata.getContentType());
                        String lengthInTime = objectMetadata.getUserMetaDataOf(LENGTH_IN_TIME);
                        if (StrUtil.isNotBlank(lengthInTime)) {
                            generatePresignedUrlRequest.addRequestParameter(S3_LENGTH_IN_TIME, lengthInTime);
                        }
                        String thumbnailKey = objectMetadata.getUserMetaDataOf(COVER_THUMBNAIL);
                        if (StrUtil.isNotBlank(thumbnailKey)) {
                            // 如果有视频/GIF封面缩略图
                            URL thumbnailUrl = transferManager.getAmazonS3Client().generatePresignedUrl(BUCKET_NAME, thumbnailKey, date);
                            generatePresignedUrlRequest.addRequestParameter(S3_THUMBNAIL, thumbnailUrl.toString());
                        }
                        presignedUrl = transferManager.getAmazonS3Client().generatePresignedUrl(generatePresignedUrlRequest);
                        // 减掉代码执行时间
                        redisComponent.save(redisKey, presignedUrl.toString(), duration.minusSeconds(7L));
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
        return presignedUrl;
    }

    /**
     * 获取URL对象
     *
     * @param url url字符串
     * @return URL
     */
    @SneakyThrows
    private static URL toUrl(String url) {
        return StrUtil.isBlank(url) ? null : new URL(url);
    }

    /**
     * 根据key判断文件对象是否存在
     *
     * @param key S3对象的键
     * @return boolean
     */
    public static boolean doesObjectExist(String key) {
        return transferManager.getAmazonS3Client().doesObjectExist(BUCKET_NAME, key);
    }

    /**
     * 从 Amazon S3 检索对象
     *
     * @param key S3对象的键
     * @return S3Object
     */
    public static S3Object getObject(String key) {
        return transferManager.getAmazonS3Client().getObject(BUCKET_NAME, key);
    }

    /**
     * 获取指定 Amazon S3 对象的元数据，而不实际获取对象本身。
     * 这在仅获取对象元数据时很有用，并避免在获取对象数据时浪费带宽。
     * 对象元数据包含内容类型、内容配置等信息，以及可以与 Amazon S3 中的对象相关联的自定义用户元数据
     *
     * @param key S3对象的键
     * @return ObjectMetadata
     */
    public static ObjectMetadata getObjectMetadata(String key) {
        try {
            return transferManager.getAmazonS3Client().getObjectMetadata(BUCKET_NAME, key);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 获取文件存储的完整路径（Key）
     *
     * @param extension 扩展名
     * @return 完整路径
     */
    public static String getFileObjKey(String extension) {
        String dateFormat = LocalDate.now().format(TakeshiDatePattern.SLASH_SEPARATOR_DATE_PATTERN_FORMATTER);
        return StrUtil.builder(StrUtil.removePrefix(extension, StrUtil.DOT), StrUtil.SLASH, dateFormat, StrUtil.SLASH, IdUtil.fastSimpleUUID(), extension).toString();
    }

    /**
     * 获取视频/GIF封面缩略图文件存储的完整路径（Key）
     *
     * @return 完整路径
     */
    public static String getThumbnailObjKey() {
        String dateFormat = LocalDate.now().format(TakeshiDatePattern.SLASH_SEPARATOR_DATE_PATTERN_FORMATTER);
        return StrUtil.builder("thumbnail", StrUtil.SLASH, dateFormat, StrUtil.SLASH, IdUtil.fastSimpleUUID(), StrUtil.DOT, ImgUtil.IMAGE_TYPE_JPG).toString();
    }

}
