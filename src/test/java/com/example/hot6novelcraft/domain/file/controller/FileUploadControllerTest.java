package com.example.hot6novelcraft.domain.file.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.FileExceptionEnum;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileUploadController 테스트")
class FileUploadControllerTest {

    @InjectMocks
    private FileUploadController fileUploadController;

    @Mock
    private FileUploadService fileUploadService;

    private MockMvc mockMvc;

    private static final Long USER_ID = 1L;
    private static final String S3_URL = "https://hot6-novelcraft-chat.s3.ap-northeast-2.amazonaws.com/chat/file-uuid.jpg";

    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileUploadController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()
                )
                .build();

        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(user.getRole()).willReturn(com.example.hot6novelcraft.domain.user.entity.enums.UserRole.READER);
        given(user.getPassword()).willReturn("password");
        given(user.getEmail()).willReturn("test@test.com");

        userDetails = new UserDetailsImpl(user, null);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // uploadChatFile() - 채팅 파일 업로드
    // =========================================================
    @Nested
    @DisplayName("POST /api/files/chat - 채팅 파일 업로드")
    class UploadChatFileTest {

        @Test
        @DisplayName("성공 - 이미지 파일 업로드")
        void uploadChatFile_imageFile_success() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-image.jpg",
                    "image/jpeg",
                    "test image content".getBytes()
            );
            given(fileUploadService.uploadChatFile(any())).willReturn(S3_URL);

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("파일 업로드 완료"))
                    .andExpect(jsonPath("$.data.fileUrl").value(S3_URL));

            verify(fileUploadService, times(1)).uploadChatFile(any());
        }

        @Test
        @DisplayName("성공 - PDF 파일 업로드")
        void uploadChatFile_pdfFile_success() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "document.pdf",
                    "application/pdf",
                    "test pdf content".getBytes()
            );
            String pdfUrl = "https://hot6-novelcraft-chat.s3.ap-northeast-2.amazonaws.com/chat/file-uuid.pdf";
            given(fileUploadService.uploadChatFile(any())).willReturn(pdfUrl);

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fileUrl").value(pdfUrl));
        }

        @Test
        @DisplayName("성공 - DOCX 파일 업로드")
        void uploadChatFile_docxFile_success() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "document.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "test docx content".getBytes()
            );
            String docxUrl = "https://hot6-novelcraft-chat.s3.ap-northeast-2.amazonaws.com/chat/file-uuid.docx";
            given(fileUploadService.uploadChatFile(any())).willReturn(docxUrl);

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fileUrl").value(docxUrl));
        }

        @Test
        @DisplayName("실패 - 빈 파일 업로드 시 예외")
        void uploadChatFile_emptyFile_throwsException() throws Exception {
            // given
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file",
                    "empty.jpg",
                    "image/jpeg",
                    new byte[0]
            );
            given(fileUploadService.uploadChatFile(any()))
                    .willThrow(new ServiceErrorException(FileExceptionEnum.ERR_FILE_EMPTY));

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(emptyFile)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 지원하지 않는 파일 형식")
        void uploadChatFile_unsupportedFileType_throwsException() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "script.exe",
                    "application/x-msdownload",
                    "malicious content".getBytes()
            );
            given(fileUploadService.uploadChatFile(any()))
                    .willThrow(new ServiceErrorException(FileExceptionEnum.ERR_FILE_NOT_SUPPORTED));

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 파일 크기 초과 (10MB)")
        void uploadChatFile_fileTooLarge_throwsException() throws Exception {
            // given
            MockMultipartFile largeFile = new MockMultipartFile(
                    "file",
                    "large-file.jpg",
                    "image/jpeg",
                    new byte[11 * 1024 * 1024] // 11MB
            );
            given(fileUploadService.uploadChatFile(any()))
                    .willThrow(new ServiceErrorException(FileExceptionEnum.ERR_FILE_TOO_LARGE));

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(largeFile)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isPayloadTooLarge());
        }

        @Test
        @DisplayName("실패 - S3 업로드 실패 시 예외")
        void uploadChatFile_s3UploadFailed_throwsException() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );
            given(fileUploadService.uploadChatFile(any()))
                    .willThrow(new ServiceErrorException(FileExceptionEnum.ERR_FILE_UPLOAD_FAILED));

            // when & then
            mockMvc.perform(multipart("/api/files/chat")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("검증 - 서비스에 MultipartFile 전달")
        void uploadChatFile_passesMultipartFileToService() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );
            given(fileUploadService.uploadChatFile(any())).willReturn(S3_URL);

            // when
            mockMvc.perform(multipart("/api/files/chat")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk());

            // then
            verify(fileUploadService, times(1)).uploadChatFile(any());
        }
    }
}
