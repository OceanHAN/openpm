<template>
  <Dialog v-model="dialogVisible" title="提交评审" width="620">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="评审绑定到版本"
      :description="`本次提交评审的是 v${version}。所有评审人都提交意见后，系统才会按聚合规则流转需求状态。`"
    />
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="评审人" prop="reviewers">
        <el-select
          v-model="formData.reviewers"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="输入账号后回车，可添加多个"
          class="w-full"
        >
          <el-option v-for="u in userList" :key="u" :label="u" :value="u" />
        </el-select>
        <div class="text-gray-400 text-12px mt-5px">
          评审人用「账号」标识（如 admin）。也可以直接输入账号回车添加。
        </div>
      </el-form-item>
      <el-form-item label="提交说明" prop="comment">
        <el-input v-model="formData.comment" type="textarea" :rows="3" placeholder="请说明本次评审的重点" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary">提交评审</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as StoryApi from '@/api/zentao/story'

defineOptions({ name: 'ZentaoStoryReviewStartForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formRef = ref()
const version = ref(1)
const userList = ref<string[]>(['admin'])

const formData = ref<{ id: number; reviewers: string[]; comment: string }>({
  id: 0,
  reviewers: [],
  comment: ''
})

const formRules = reactive({
  reviewers: [
    {
      required: true,
      validator: (_r: any, v: string[], cb: any) =>
        v && v.length > 0 ? cb() : cb(new Error('评审人不能为空')),
      trigger: 'change'
    }
  ]
})

/** 打开弹窗 */
const open = async (id: number) => {
  dialogVisible.value = true
  const story = await StoryApi.getStory(id)
  version.value = story.version ?? 1
  formData.value = { id, reviewers: ['admin'], comment: '' }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  await StoryApi.startReview(formData.value)
  message.success('已提交评审，需求进入评审中')
  dialogVisible.value = false
  emit('success')
}
</script>
