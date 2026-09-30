<template>
  <Dialog v-model="dialogVisible" title="完成任务" width="520">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      class="mb-15px"
      title="剩余工时归零才算真正完成"
      description="禅道的工时模型：登记本次消耗与剩余。剩余大于 0 时任务会回到「进行中」，只有归零才转为「已完成」。"
    />
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="当前已消耗">
        <span class="text-gray-600">{{ currentConsumed }} 工时</span>
      </el-form-item>
      <el-form-item label="本次消耗" prop="consumed">
        <el-input-number v-model="formData.consumed" :min="0" :precision="2" class="w-full" />
      </el-form-item>
      <el-form-item label="剩余工时" prop="left">
        <el-input-number v-model="formData.left" :min="0" :precision="2" class="w-full" />
      </el-form-item>
      <el-form-item label="完成说明" prop="comment">
        <el-input v-model="formData.comment" type="textarea" :rows="3" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary">提 交</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as TaskApi from '@/api/zentao/task'

defineOptions({ name: 'ZentaoTaskFinishForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formRef = ref()
const currentConsumed = ref(0)
const formData = ref<TaskApi.TaskFinishVO>({ id: 0, consumed: 0, left: 0, comment: '' })

const formRules = reactive({
  consumed: [{ required: true, message: '本次消耗不能为空', trigger: 'blur' }],
  left: [{ required: true, message: '剩余工时不能为空', trigger: 'blur' }]
})

const open = async (id: number) => {
  dialogVisible.value = true
  const task = await TaskApi.getTask(id)
  currentConsumed.value = Number(task.consumed ?? 0)
  formData.value = {
    id,
    consumed: 0,
    left: Number(task.left ?? 0),
    comment: ''
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  await TaskApi.finishTask(formData.value)
  message.success('已提交')
  dialogVisible.value = false
  emit('success')
}
</script>
