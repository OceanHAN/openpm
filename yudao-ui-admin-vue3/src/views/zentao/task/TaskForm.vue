<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="700">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入任务名称" maxlength="255" show-word-limit />
      </el-form-item>

      <el-row>
        <el-col :span="12">
          <el-form-item label="所属项目" prop="project">
            <el-input-number v-model="formData.project" :min="1" :controls="false" class="w-full" placeholder="项目编号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属执行" prop="execution">
            <el-select v-model="formData.execution" placeholder="请选择执行" clearable filterable class="w-full">
              <el-option v-for="e in executionList" :key="e.id" :label="e.name" :value="e.id!" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="所属模块" prop="module">
        <ZentaoModuleSelect v-model="formData.module" :root="formData.execution" type="task" />
        <div v-if="!formData.execution" class="text-12px text-gray-400">先选择执行，再选择模块（任务模块树挂在执行下）</div>
      </el-form-item>

      <el-row>
        <el-col :span="12">
          <el-form-item label="关联需求" prop="story">
            <el-input-number v-model="formData.story" :min="0" :controls="false" class="w-full" placeholder="需求编号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="任务类型" prop="type">
            <el-select v-model="formData.type" placeholder="请选择类型" class="w-full">
              <el-option v-for="o in TASK_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="8">
          <el-form-item label="优先级" prop="pri">
            <el-select v-model="formData.pri" placeholder="优先级" class="w-full">
              <el-option v-for="o in PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="预计工时" prop="estimate">
            <el-input-number v-model="formData.estimate" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="剩余工时" prop="left">
            <el-input-number v-model="formData.left" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="截止日期" prop="deadline">
            <el-date-picker v-model="formData.deadline" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="指派给" prop="assignedTo">
            <el-input v-model="formData.assignedTo" placeholder="账号，如 admin" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="任务描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="4" placeholder="请输入任务描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as TaskApi from '@/api/zentao/task'
import * as ExecutionApi from '@/api/zentao/execution'
import ZentaoModuleSelect from '@/views/zentao/components/ModuleSelect.vue'
import { TASK_TYPE_OPTIONS, PRI_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoTaskForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()

const formData = ref<TaskApi.TaskVO>({})

const formRules = reactive({
  name: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
  project: [{ required: true, message: '所属项目不能为空', trigger: 'blur' }],
  pri: [{ required: true, message: '优先级不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number) => {
  await loadExecutions()
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增任务' : '编辑任务'
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await TaskApi.getTask(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await TaskApi.createTask(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await TaskApi.updateTask(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const executionList = ref<ExecutionApi.ExecutionVO[]>([])

const loadExecutions = async () => {
  try {
    const data = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
    executionList.value = data.list
  } catch {
    executionList.value = []
  }
}

const resetForm = () => {
  formData.value = {
    project: 1,
    execution: 0,
    module: 0,
    story: 0,
    name: '',
    type: 'devel',
    pri: 3,
    estimate: 0,
    left: 0,
    assignedTo: '',
    desc: ''
  }
  formRef.value?.resetFields()
}
</script>
