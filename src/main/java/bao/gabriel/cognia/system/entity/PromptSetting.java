package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_prompt_setting")
public class PromptSetting {

    public static final long SINGLETON_ID = 1L;

    public static final String DEFAULT_IMAGE_CAPTION_PROMPT = """
            请为这张图片生成适合知识库检索的结构化描述，使用中文，按以下小节输出：
            1) 图类型：流程图 / 架构图 / 折线图 / 表格截图 / 界面截图 / 其他；
            2) 主体与结构：图片在讲什么关系、流程从哪到哪、分为几个阶段；
            3) 图中全部可读文字：按阅读顺序保留 OCR 原文；
            4) 关键数值与结论：列出数字、单位、同比/环比等结论；
            5) 可回答的问题：写出 3-5 个这张图能回答的问题。
            不要臆造图片中不存在的信息。""";

    public String imageCaptionPrompt() {
        return imageCaptionPrompt == null || imageCaptionPrompt.isBlank()
                ? DEFAULT_IMAGE_CAPTION_PROMPT : imageCaptionPrompt;
    }

    @TableId(type = IdType.INPUT)
    private Long id;
    private String systemPrompt;
    private String ragPrompt;
    private String expiredPrompt;
    private String toolPrompt;
    private String compressionPrompt;
    private String imageCaptionPrompt;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
