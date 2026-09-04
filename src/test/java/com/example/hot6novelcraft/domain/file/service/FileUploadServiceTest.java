package com.example.hot6novelcraft.domain.file.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.FileExceptionEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileUploadService 테스트")
class FileUploadServiceTest {

    @InjectMocks
    private FileUploadService fileUploadService;

    @Mock
    private S3Client s3Client;

    private static final String BUCKET_NAME = "hot6-novelcraft-chat";
    private static final String REGION = "ap-northeast-2";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileUploadService, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(fileUploadService, "region", REGION);
    }

    // =========================================================
    // uploadChatFile() - 파일 업로드
    // =========================================================
    @Nested
    @DisplayName("uploadChatFile() - 파일 업로드")
    class UploadChatFileTest {

        @Test
        @DisplayName("성공 - JPG 이미지 업로드")
        void uploadChatFile_jpgImage_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    "image/jpeg",
                    "test image content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).startsWith("https://" + BUCKET_NAME + ".s3." + REGION + ".amazonaws.com/chat/");
            assertThat(result).endsWith(".jpg");
            verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("성공 - PNG 이미지 업로드")
        void uploadChatFile_pngImage_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.png",
                    "image/png",
                    "test image content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).endsWith(".png");
        }

        @Test
        @DisplayName("성공 - PDF 문서 업로드")
        void uploadChatFile_pdfDocument_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "document.pdf",
                    "application/pdf",
                    "test pdf content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).endsWith(".pdf");
        }

        @Test
        @DisplayName("성공 - DOCX 문서 업로드")
        void uploadChatFile_docxDocument_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "document.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "test docx content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).endsWith(".docx");
        }

        @Test
        @DisplayName("성공 - XLSX 스프레드시트 업로드")
        void uploadChatFile_xlsxDocument_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "spreadsheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "test xlsx content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).endsWith(".xlsx");
        }

        @Test
        @DisplayName("검증 - S3 Key는 chat/ prefix와 UUID 포함")
        void uploadChatFile_s3KeyFormat_valid() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).contains("/chat/");
            // UUID 형식 검증 (간단히 길이 체크)
            String[] parts = result.split("/chat/");
            assertThat(parts[1]).hasSizeGreaterThan(30); // UUID는 최소 36자 + 확장자
        }
    }

    // =========================================================
    // validateFile() - 파일 검증
    // =========================================================
    @Nested
    @DisplayName("파일 검증 - validateFile()")
    class ValidateFileTest {

        @Test
        @DisplayName("실패 - null 파일")
        void validateFile_nullFile_throwsException() {
            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(null))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_EMPTY.getMessage());
        }

        @Test
        @DisplayName("실패 - 빈 파일")
        void validateFile_emptyFile_throwsException() {
            // given
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file",
                    "empty.jpg",
                    "image/jpeg",
                    new byte[0]
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(emptyFile))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_EMPTY.getMessage());
        }

        @Test
        @DisplayName("실패 - 파일 크기 초과 (10MB)")
        void validateFile_fileTooLarge_throwsException() {
            // given
            byte[] largeContent = new byte[(int) (MAX_FILE_SIZE + 1)];
            MockMultipartFile largeFile = new MockMultipartFile(
                    "file",
                    "large.jpg",
                    "image/jpeg",
                    largeContent
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(largeFile))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_TOO_LARGE.getMessage());
        }

        @Test
        @DisplayName("실패 - 파일명이 null")
        void validateFile_nullFilename_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    null,
                    "image/jpeg",
                    "content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_EMPTY.getMessage());
        }

        @Test
        @DisplayName("실패 - 파일명이 빈 문자열")
        void validateFile_blankFilename_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "   ",
                    "image/jpeg",
                    "content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_EMPTY.getMessage());
        }

        @Test
        @DisplayName("실패 - contentType이 null")
        void validateFile_nullContentType_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    null,
                    "content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_NOT_SUPPORTED.getMessage());
        }

        @Test
        @DisplayName("실패 - 확장자 없는 파일명")
        void validateFile_noExtension_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "filenamewithoutextension",
                    "image/jpeg",
                    "content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_NOT_SUPPORTED.getMessage());
        }

        @Test
        @DisplayName("실패 - 지원하지 않는 확장자")
        void validateFile_unsupportedExtension_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "script.exe",
                    "application/x-msdownload",
                    "malicious content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_NOT_SUPPORTED.getMessage());
        }

        @Test
        @DisplayName("보안 - 확장자-MIME 불일치 우회 방지 (jpg 파일인데 application/pdf)")
        void validateFile_extensionMimeMismatch_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "image.jpg",
                    "application/pdf", // JPG인데 PDF MIME
                    "fake content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_NOT_SUPPORTED.getMessage());
        }

        @Test
        @DisplayName("보안 - MIME 타입 정규화 (charset 파라미터 제거)")
        void validateFile_mimeTypeWithCharset_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "document.txt",
                    "text/plain; charset=utf-8", // charset 포함
                    "test content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).endsWith(".txt");
        }

        @Test
        @DisplayName("보안 - 대소문자 구분 없는 확장자 검증")
        void validateFile_caseInsensitiveExtension_success() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.JPG", // 대문자 확장자
                    "image/jpeg",
                    "test content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            assertThat(result).endsWith(".jpg"); // 소문자로 변환됨
        }
    }

    // =========================================================
    // S3 업로드 실패 처리
    // =========================================================
    @Nested
    @DisplayName("S3 업로드 실패 처리")
    class S3UploadFailureTest {

        @Test
        @DisplayName("실패 - IOException 발생 시 예외 처리")
        void uploadChatFile_ioException_throwsException() throws IOException {
            // given
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(false);
            given(file.getSize()).willReturn(1000L);
            given(file.getOriginalFilename()).willReturn("test.jpg");
            given(file.getContentType()).willReturn("image/jpeg");
            given(file.getInputStream()).willThrow(new IOException("File read error"));

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_UPLOAD_FAILED.getMessage());
        }

        @Test
        @DisplayName("실패 - S3 putObject 실패 시 예외 처리")
        void uploadChatFile_s3PutObjectFails_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willThrow(new RuntimeException("S3 upload failed"));

            // when & then
            assertThatThrownBy(() -> fileUploadService.uploadChatFile(file))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(FileExceptionEnum.ERR_FILE_UPLOAD_FAILED.getMessage());
        }
    }

    // =========================================================
    // buildS3Url() - S3 URL 생성
    // =========================================================
    @Nested
    @DisplayName("S3 URL 생성 검증")
    class BuildS3UrlTest {

        @Test
        @DisplayName("검증 - S3 URL 형식이 올바른지 확인")
        void uploadChatFile_s3UrlFormat_valid() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(null);

            // when
            String result = fileUploadService.uploadChatFile(file);

            // then
            String expectedUrlPrefix = "https://" + BUCKET_NAME + ".s3." + REGION + ".amazonaws.com/chat/";
            assertThat(result).startsWith(expectedUrlPrefix);
        }
    }
}
