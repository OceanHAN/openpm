<template>
  <Dialog v-model="dialogVisible" title="变更需求（正式变更）" width="680">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      class="mb-15px"
      title="此操作会产生新版本"
      :description="`当前版本为 v${oldVersion}，提交后将生成 v${oldVersion + 1}，历史版本会完整保留。`"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="需求标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入变更后的标题" maxlength="255" />
      </el-form-item>
      <el-form-item label="需求描述" prop="spec">
        <el-input v-model="formData.spec" type="textarea" :rows="5" placeholder="请输入变更后的需求描述" />
      </el-form-item>
      <el-form-item label="验收标准" prop="verify">
        <el-input v-model="formData.verify" type="textarea" :rows="3" placeholder="请输入变更后的验收标准" />
      </el-form-item>
      <el-form-item label="指派给" prop="assignedTo">
        <el-input v-model="formData.assignedTo" placeholder="留空则不改变" />
      </el-form-item>
      <el-form-item label="变更说明" prop="comment">
        <el-input v-model="formData.comment" type="textarea" :rows="2" placeholder="本次变更的原因或背景" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">提交变更</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as StoryApi from '@/api/zentao/story'

defineOptions({ name: 'ZentaoStoryChangeForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const oldVersion = ref(1)

const formData = ref<StoryApi.StoryChangeVO>({
  id: 0,
  title: '',
  spec: '',
  verify: '',
  assignedTo: '',
  comment: ''
})

const formRules = reactive({
  title: [{ required: true, message: '需求标题不能为空', trigger: 'blur' }]
})

/** 打开弹窗：先取当前内容作为初值 */
const open = async (id: number) => {
  dialogVisible.value = true
  formLoading.value = true
  try {
    const story = await StoryApi.getStory(id)
    oldVersion.value = story.version ?? 1
    formData.value = {
      id,
      title: story.title ?? '',
      spec: story.spec ?? '',
      verify: story.verify ?? '',
      assignedTo: story.assignedTo ?? '',
      comment: ''
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    const newVersion = await StoryApi.changeStory(formData.value)
    message.success(`变更成功，已生成 v${newVersion}`)
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
